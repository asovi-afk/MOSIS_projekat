package com.mosis.stepby.viewmodels

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.mosis.stepby.utils.*
import com.mosis.stepby.utils.running.IndependentRun
import com.mosis.stepby.utils.running.Track
import com.mosis.stepby.utils.running.TrackFlooring
import com.mosis.stepby.utils.running.TrackRun
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.osmdroid.util.GeoPoint
import java.lang.Integer.min
import java.util.UUID

/*
* NOTES:
*   1. addSnapshotListener on collection or document will trigger at least once on adding them.
* */


class HomeFragmentViewModel: ViewModel() {

    val showOtherUsers = MutableLiveData<Boolean>(true)
    val otherUsersChanges = MutableLiveData<List<OtherUserInfo>>()

    private val _instantToast = MutableLiveData<String>()
    val instantToast: LiveData<String> get() = _instantToast

    private val bitmapList: MutableList<Pair<String, Bitmap>> = mutableListOf()
    private val mutex = Mutex()  // Used to lock for simultaneous changes on bitmapList, otherUsersChanges and friendList.

    private val locationSnapshotCH = Channel<Pair<List<DocumentSnapshot>,List<DocumentChange>>>(Channel.CONFLATED)

    private var coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val firestore = Firebase.firestore
    private val storageRef = Firebase.storage.reference
    private lateinit var userEmail: String
    private var friendList: List<String> = listOf()

    private var onLocationChangeRegistration: ListenerRegistration? = null
    private lateinit var onFriendListChangeRegistration: ListenerRegistration


    override fun onCleared() {
        stopListeningForLocationChanges()
        super.onCleared()
    }

    fun startListeningForLocationChanges() {
        if (onLocationChangeRegistration == null){
            userEmail = Firebase.auth.currentUser!!.email!!
            onLocationChangeRegistration = firestore.collection(FirestoreCollections.USER_LOCATIONS).addSnapshotListener { value, error ->
                if (error == null && value != null) coroutineScope.launch { locationSnapshotCH.send(Pair(value.documents, value.documentChanges)) }
            }

            onFriendListChangeRegistration = firestore.collection(FirestoreCollections.USERS).document(userEmail).addSnapshotListener {value, error ->
                if (error == null && value != null) coroutineScope.launch { mutex.withLock { friendList = value.get(UserInfoKeys.FRIENDS) as List<String> };  resendOtherUsersLocaton()}
            }
            // start coroutine that will listen on locationSnapshotCH for newest positions
            coroutineScope.launch { onLocationChangeLogic() }

            coroutineScope.launch { ultimateBitmapCacheMaintenance() }
        }
    }

    fun stopListeningForLocationChanges() {
        if(onLocationChangeRegistration != null) {
            onLocationChangeRegistration!!.remove()
            onLocationChangeRegistration = null
            onFriendListChangeRegistration.remove()
            coroutineScope.cancel()
            coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
    }

    fun resendOtherUsersLocaton() {
        coroutineScope.launch {
            val snapshot = firestore.collection(FirestoreCollections.USER_LOCATIONS).get().await()
            mutex.withLock {
                val latestPositions = formatPositions(snapshot.documents, listOf())
                otherUsersChanges.postValue(latestPositions)
            }
        }
    }

    fun uploadRun(run: IndependentRun, name: String?) {
        val runInfo  = run.formatForUpload(name, userEmail)
        firestore.collection(FirestoreCollections.RUNS).add(runInfo)
            .addOnSuccessListener { _instantToast.value = "Run saved." }
            .addOnFailureListener { _instantToast.value = "Run not saved." }
    }

    fun uploadRunWithTrack(run: IndependentRun, _runName: String?, _trackName: String?, flooring: TrackFlooring) {
        coroutineScope.launch {
            val trackName = if(_trackName.isNullOrBlank()) Track.generateDefaultName(userEmail, firestore) else _trackName
            val trackInfo = Track.formatForUpload(run.path, userEmail, flooring, trackName)
            val trackID = firestore.collection(FirestoreCollections.TRACKS).add(trackInfo).await().id

            val runInfo = TrackRun.formatIndependentRunForUpload(run, _runName, trackID, userEmail)
            firestore.collection(FirestoreCollections.RUNS).add(runInfo)
                .addOnSuccessListener { _instantToast.value = "Run saved." }
                .addOnFailureListener { _instantToast.value = "Run not saved." }
        }
    }

    private suspend fun onLocationChangeLogic() {
        while(true) {
            val positionDocuments = locationSnapshotCH.receive()
            mutex.withLock {
                val latestPositions = formatPositions(positionDocuments.first, positionDocuments.second)
                otherUsersChanges.postValue(latestPositions)
            }
        }
    }

    private suspend fun getUserProfilePic(email: String): Bitmap {
        // Try in cache
        var bitmap = bitmapList.firstOrNull { pair -> pair.first == email }?.second
        if (bitmap == null) {
            // fetch from storage
            val picName = firestore.collection(FirestoreCollections.USERS).document(email).get().await().getString(UserInfoKeys.PROFILE_PICTURE) as String
            val data = storageRef.child(StorageFolders.IMAGES).child(picName).getBytes(
                ONE_MEGABYTE).await()
            bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            bitmap = getCircularBitmap(bitmap)
            // Add to cache if necessary
            synchronized(bitmapList) {
                if (bitmapList.firstOrNull { pair -> pair.first == email } == null)
                    bitmapList.add(Pair(email, bitmap))
            }
        }
        return bitmap
    }

    private suspend fun formatPositions(documentSnapshot: List<DocumentSnapshot>, documentChanges: List<DocumentChange>): List<OtherUserInfo> {
        val newPositionList = mutableListOf<OtherUserInfo>()
        runBlocking {
            // Formatting result list
            for (document in documentSnapshot) {
                launch {
                    try {
                        if(document.id != userEmail) {
                            val email = document.id
                            val point = document.getGeoPoint(UserLocationKeys.POINT) as com.google.firebase.firestore.GeoPoint
                            val gPoint = GeoPoint(point.latitude, point.longitude)
                            var bitmap: Bitmap? = null
                            if (friendList.contains(document.id))  bitmap = getUserProfilePic(email)
                            synchronized(newPositionList) { newPositionList.add(OtherUserInfo(email, gPoint, bitmap)) }
                        }
                    }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { Log.d(TAG,"getLocationChanges: " + e.message) }
                }
            }
            // Releasing bitmap cache
            launch {
                for(change in documentChanges) {
                    if (change.type == DocumentChange.Type.REMOVED)
                        synchronized(bitmapList) {bitmapList.removeIf { pair -> pair.first == change.document.id }}
                }
            }
        }
        return newPositionList
    }

    private suspend fun ultimateBitmapCacheMaintenance() {
        while(true) {
            delay(BITMAP_CACHE_MAINTENANCE_CYCLE)
            mutex.withLock {
                if (friendList.isNotEmpty()) {
                    val bitmapsToRemove = mutableListOf<Pair<String, Bitmap>>()
                    for (pair in bitmapList)
                        if (!friendList.contains(pair.first)) bitmapsToRemove.add(pair)
                    for (pair in bitmapsToRemove) bitmapList.remove(pair)
                }
            }
        }
    }

    private fun getCircularBitmap(oldBitmap: Bitmap): Bitmap {
        val bitmap = scaleBitmap(oldBitmap)
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val cx = bitmap.width / 2f
        val cy = bitmap.height / 2f
        val radius = cx.coerceAtMost(cy)
        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val factor = if (bitmap.height > bitmap.width)
            BITMAP_RADIUS / bitmap.width.toFloat()
        else
            BITMAP_RADIUS / bitmap.height.toFloat()

        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).toInt(), (bitmap.height * factor).toInt(), true)
    }

    companion object {
        private const val TAG = "HomeFragmentViewModel"
        private const val ONE_MEGABYTE = 1024 * 1024.toLong()
        private const val BITMAP_CACHE_MAINTENANCE_CYCLE = 1000 * 60 * 10.toLong() // in milliseconds (10 min)
        private const val BITMAP_RADIUS = 70
    }
}