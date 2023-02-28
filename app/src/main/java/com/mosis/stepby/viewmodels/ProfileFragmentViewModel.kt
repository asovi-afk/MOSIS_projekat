package com.mosis.stepby.viewmodels

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.mosis.stepby.utils.*
import com.mosis.stepby.utils.MemoryValues.ONE_MEGABYTE
import com.mosis.stepby.utils.running.ranking.RankingManager
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

// Note:
// This fragment is not listening to any changes in firestore database.

open class ProfileFragmentViewModel: ViewModel() {

    val selectedTab = MutableLiveData<ProfileTab>(ProfileTab.RUNS)

    private val _userBasicInfo = MutableLiveData<BasicProfileInfo>()
    val userBasicInfo: LiveData<BasicProfileInfo> get() = _userBasicInfo

    protected val _instantToast = MutableLiveData<String?>()
    val instantToast: LiveData<String?> get() = _instantToast
    private val _runList = MutableLiveData<List<RunInfo>>()
    val runList: LiveData<List<RunInfo>> get() = _runList
    private val _friendList = MutableLiveData<List<FriendInfo>>(listOf())
    val friendList: LiveData<List<FriendInfo>> get() = _friendList
    private val _pendingFriendList = MutableLiveData<List<FriendInfo>>(listOf())
    val pendingFriendList: LiveData<List<FriendInfo>> get() = _pendingFriendList

    protected var coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    protected val firestore = Firebase.firestore
    private val storageRef = Firebase.storage.reference

    override fun onCleared() {
        coroutineScope.cancel()
        super.onCleared()
    }

     fun update() {
        Log.d(TAG, "update()")
        val email = Firebase.auth.currentUser!!.email!!
        coroutineScope.launch(SupervisorJob()) {
            launch { updateUserBasicInfo(email) }
            launch { updateRunList(email) }
            launch { updateFriendList(email) }
            launch { updatePendingFriendList(email) }
        }
    }

    fun updateRanking() {
        coroutineScope.launch {
            Log.d(TAG, "updateRanking()")
            val profileInfo = _userBasicInfo.value!!
            val rankingInfo = withTimeoutOrNull(DurationValues.PROFILE_UPDATE_LIMIT) { RankingManager.getUserRanking(profileInfo.email!!) }

            if (rankingInfo != null) _userBasicInfo.postValue(BasicProfileInfo(profileInfo.username, profileInfo.email, profileInfo.bitmap, rankingInfo.first, rankingInfo.second))
            else { _instantToast.postValue("Could not get new ranking data."); _userBasicInfo.postValue(BasicProfileInfo(profileInfo.username, profileInfo.email, profileInfo.bitmap, null, null)) }
        }
    }

    fun removeFriend(friendEmail: String, views: List<View>) {
        views.forEach { it.isEnabled = false }

        val userEmail = _userBasicInfo.value!!.email!!
        val userRef = firestore.collection(FirestoreCollections.USERS).document(userEmail)
        val friendRef = firestore.collection(FirestoreCollections.USERS).document(friendEmail)
        firestore.runTransaction {
            val userFriends = it.get(userRef).get(UserInfoKeys.FRIENDS) as MutableList<String>
            userFriends.remove(friendEmail)
            val friendFriends = it.get(friendRef).get(UserInfoKeys.FRIENDS) as MutableList<String>
            friendFriends.remove(userEmail)

            it.update(userRef, UserInfoKeys.FRIENDS, userFriends)
            it.update(friendRef, UserInfoKeys.FRIENDS, friendFriends)
        }.addOnSuccessListener { coroutineScope.launch { updateFriendList(userEmail) } }
            .addOnFailureListener { try {
                views.forEach { it.isEnabled = true }
                _instantToast.value = "Could not remove user from friends."
            }
            // In case view is destroyed before onFailureListener executes
            catch (_: Exception) { }
            }
    }

    fun declineFriendship(friendEmail: String, views: List<View>) {
        views.forEach { it.isEnabled = false }
        val userEmail = _userBasicInfo.value!!.email!!
        val usersAskingRef = firestore.collection(FirestoreCollections.PENDING_FRIENDSHIPS).document(userEmail)
        firestore.runTransaction {
            val usersAsking = it.get(usersAskingRef).get(PendingFriendshipKeys.FROM) as MutableList<String>
            usersAsking.remove(friendEmail)
            it.update(usersAskingRef, PendingFriendshipKeys.FROM, usersAsking)
        }.addOnSuccessListener { coroutineScope.launch { updatePendingFriendList(userEmail) } }
            .addOnFailureListener {  try {
                views.forEach { it.isEnabled = true }
                _instantToast.value = "Could not decline friend request."
            }
            // In case view is destroyed before onFailureListener executes
            catch (_: Exception) { }
            }
    }

    fun acceptFriendship(friendEmail: String, views: List<View>) {
        views.forEach { it.isEnabled = false }
        val userEmail = _userBasicInfo.value!!.email!!
        val usersAskingRef = firestore.collection(FirestoreCollections.PENDING_FRIENDSHIPS).document(userEmail)
        val userRef = firestore.collection(FirestoreCollections.USERS).document(userEmail)
        val friendRef =firestore.collection(FirestoreCollections.USERS).document(friendEmail)
        firestore.runTransaction {
            // removing from pending collection
            val usersAsking = it.get(usersAskingRef).get(PendingFriendshipKeys.FROM) as MutableList<String>
            usersAsking.remove(friendEmail)

            // Adding emails to respective friend lists
            val friendFriends = it.get(friendRef).get(UserInfoKeys.FRIENDS) as MutableList<String>
            val userFriends = it.get(userRef).get(UserInfoKeys.FRIENDS) as MutableList<String>

            userFriends.add(friendEmail)
            friendFriends.add(userEmail)
            // All writes inside transaction must be preformed after all reads
            it.update(usersAskingRef, PendingFriendshipKeys.FROM, usersAsking)
            it.update(userRef, UserInfoKeys.FRIENDS, userFriends)
            it.update(friendRef, UserInfoKeys.FRIENDS, friendFriends)
        }.addOnSuccessListener { coroutineScope.launch { updatePendingFriendList(userEmail); updateFriendList(userEmail) } }
            .addOnFailureListener {  try {
                views.forEach { it.isEnabled = true }
                _instantToast.value = "Could not accept friend request."
            }
            // In case view is destroyed before onFailureListener executes
            catch (_: Exception) { }
            }
    }

    suspend fun updateRunList(email: String) {
        try {
            val updatedList = getRunListOfUser(email, firestore)
            _runList.postValue(updatedList)
        } catch (e: Exception) {
            e.message?.let { Log.d(TAG, it) }
            e.localizedMessage?.let { Log.d(TAG, it)}
        }
    }

    suspend fun updateUserBasicInfo(email: String) {
        val newProfileInfo = withTimeoutOrNull(DurationValues.PROFILE_UPDATE_LIMIT) {
            getBasicProfileInfo(email, firestore, storageRef)
        }

        if(newProfileInfo != null) _userBasicInfo.postValue(newProfileInfo)
        else { _instantToast.postValue("Could not get profile data."); _userBasicInfo.postValue(BasicProfileInfo(email = email)) }
    }

    suspend fun updateFriendList(email: String) {
        try {
            val emailList = getFriendList(email, firestore)
            val infoList = getFriendInfoList(emailList!!, firestore, storageRef)
            if (infoList != null) _friendList.postValue(infoList)
        } catch (e: Exception) {_instantToast.postValue("Could not get friends data.")}
    }

    suspend fun updatePendingFriendList(email: String) {
        try {
            val emailList = getPendingFriendList(email, firestore)
            val infoList = getFriendInfoList(emailList!!, firestore, storageRef)
            if (infoList != null) _pendingFriendList.postValue(infoList)
        } catch (e: Exception) {_instantToast.postValue("Could not get friends data.")}
    }

    fun clearInstantToast() { _instantToast.value = null}

    companion object {
        private const val TAG = "ProfileFragmentViewModel"
    }
}