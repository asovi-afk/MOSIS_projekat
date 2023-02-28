package com.mosis.stepby.utils

import android.graphics.BitmapFactory
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.core.OrderBy
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.storage.StorageReference
import com.mosis.stepby.utils.running.IndependentRun
import com.mosis.stepby.utils.running.Track
import com.mosis.stepby.utils.running.TrackFlooring
import com.mosis.stepby.utils.running.ranking.RankingManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

fun durationToString(start: Timestamp, end: Timestamp = Timestamp.now()): String {
    val duration = end.seconds - start.seconds
    return durationToString(duration)
}

fun durationToString(_duration: Long): String {
    var duration = _duration
    val seconds = duration % 60
    duration /= 60
    val minutes = duration % 60
    val hours = duration / 60
    return String.format("$hours:%02d:%02d", minutes, seconds)
}

// in meters
fun distanceToString(distance: Long): String {
    return if (distance < 1000) String.format("%d m", distance.toInt()) else String.format("%.2f km", distance/1000.0)
}

fun distanceToStringWithUnit(distance: Long): Pair<String,String> {
    return if (distance < 1000) Pair(distance.toInt().toString(), "m") else Pair(String.format("%.2f", distance/1000.0), "km")
}

suspend fun getRunListOfUser(email: String, firestore: FirebaseFirestore): List<RunInfo> {
    val list = mutableListOf<RunInfo>()
    val task = firestore.collection(FirestoreCollections.RUNS)
        .whereEqualTo(RunInfoKeys.CREATOR_EMAIL, email)
        .orderBy(RunInfoKeys.FINISHED_AT, Query.Direction.DESCENDING).get()
    task.await()
    if (!task.isSuccessful) return list

    task.result.documents.forEach { doc ->
        val run = getRunFromDocumentSnapshot(doc)
        if (run != null) list.add(run)
    }

    return list
}

fun getRunFromDocumentSnapshot(document: DocumentSnapshot): RunInfo? {
    return try {
        val id = document.id
        val distance = document.getLong(RunInfoKeys.DISTANCE)
        val duration = document.getLong(RunInfoKeys.DURATION)
        val name = document.getString(RunInfoKeys.NAME)
        val finishedTS = document.getTimestamp(RunInfoKeys.FINISHED_AT)
        val path = IndependentRun.mapListToPoints(document.get(RunInfoKeys.POINTS) as List<HashMap<String, Any>>)
        val trackId = document.getString(RunInfoKeys.TRACK_ID)

        RunInfo(id, name!!, distance!!, duration!!, finishedTS!!, path, trackId)
    } catch (e: Exception) { null }
}

suspend fun getFriendList(email: String, firestore: FirebaseFirestore): List<String>? {
    return try {
        val snapshot = firestore.collection(FirestoreCollections.USERS).document(email).get().await()
        snapshot.get(UserInfoKeys.FRIENDS) as List<String>
    } catch ( e: Exception) { null }
}

suspend fun getBasicProfileInfo(email: String, firestore: FirebaseFirestore, storageRef: StorageReference): BasicProfileInfo? {
    return try {
        return coroutineScope {
            val rankingStatsDer = async { RankingManager.getUserRanking(email) }
            val userSnap = firestore.collection(FirestoreCollections.USERS).document(email).get().await()
            val picName = userSnap.getString(UserInfoKeys.PROFILE_PICTURE)!!
            val bitmapDer = async { storageRef.child(StorageFolders.IMAGES).child(picName).getBytes(MemoryValues.ONE_MEGABYTE).await() }
            val username = userSnap.getString(UserInfoKeys.USERNAME)!!
            val bytes = bitmapDer.await()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val rankingStats = rankingStatsDer.await()

            BasicProfileInfo(username, email, bitmap, rankingStats.first, rankingStats.second)
        }

    } catch ( e: Exception) { null }
}

suspend fun getFriendInfo(email: String, firestore: FirebaseFirestore, storageRef: StorageReference): FriendInfo? {
    return try {
        return coroutineScope {
            val userSnap = firestore.collection(FirestoreCollections.USERS).document(email).get().await()
            val picName = userSnap.getString(UserInfoKeys.PROFILE_PICTURE)!!
            val bitmapDer = async { storageRef.child(StorageFolders.IMAGES).child(picName).getBytes(MemoryValues.ONE_MEGABYTE).await() }
            val username = userSnap.getString(UserInfoKeys.USERNAME)!!
            val bytes = bitmapDer.await()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            FriendInfo(email, username, bitmap)
        }

    } catch ( e: Exception) { null }
}

suspend fun getFriendInfoList(emails: List<String>, firestore: FirebaseFirestore, storageRef: StorageReference): List<FriendInfo>? {
    return coroutineScope {
        val listDefer = mutableListOf<Deferred<FriendInfo?>>()
        emails.forEach { listDefer.add( async { getFriendInfo(it, firestore, storageRef) }) }
        val list = mutableListOf<FriendInfo>()
        listDefer.forEach { val info = it.await(); if (info != null) list.add(info)}
        list
    }
}

suspend fun getPendingFriendList(email: String, firestore: FirebaseFirestore): List<String>? {
    return try {
        val snapshot = firestore.collection(FirestoreCollections.PENDING_FRIENDSHIPS).document(email).get().await()
        snapshot.get(PendingFriendshipKeys.FROM) as List<String>
    } catch ( e: Exception) { null }
}


fun timeStampToString(ts: Timestamp): String {
    val dt = ts.toDate()
    val dateFormat = SimpleDateFormat("dd-MMM-yy HH:mm", Locale.getDefault())
    return dateFormat.format(dt)
}

