package com.mosis.stepby.utils.running.ranking

import android.util.Log
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.utils.FirestoreCollections
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

object RankingManager {

    const val VALUE_UNKNOWN = -1L

    private val firestore = Firebase.firestore
    private val collectionRef = firestore.collection(FirestoreCollections.TOTAL_DISTANCE)
    private const val FIELD_DISTANCE = "distance"
    private const val TAG = "RankingManager"

    fun addDistance(distance: Long, email: String) {
        val ref = collectionRef.document(email)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            val newDistance = snapshot.getLong(FIELD_DISTANCE)!! + distance
            transaction.update(ref, FIELD_DISTANCE, newDistance)
        }
    }

    suspend fun getUserRanking(email: String): Pair<Long, Long> {
        val distanceTask = collectionRef.document(email).get()

        distanceTask.await()
        if (!distanceTask.isSuccessful) return Pair(VALUE_UNKNOWN, VALUE_UNKNOWN)

        val distance = distanceTask.result.getLong(FIELD_DISTANCE)!!

        return coroutineScope {

            // Number of users that ran same or more than our user.
            val byDistanceCountTask = collectionRef.orderBy(FIELD_DISTANCE, Query.Direction.DESCENDING)
                .whereGreaterThanOrEqualTo(FIELD_DISTANCE, distance)
                .count().get(AggregateSource.SERVER)

            // List used to find out placement among others with the same distance. This ranking is based by email.
            val sameDistanceListTask = collectionRef.whereEqualTo(FIELD_DISTANCE, distance).get()
            sameDistanceListTask.await()
            if (!sameDistanceListTask.isSuccessful) return@coroutineScope Pair(VALUE_UNKNOWN, distance)

            var inFrontByName = 0L
            sameDistanceListTask.result.documents.forEach { snap -> if (snap.id > email) inFrontByName++ }

            byDistanceCountTask.await()
            if (!byDistanceCountTask.isSuccessful) return@coroutineScope Pair(VALUE_UNKNOWN, distance)

            return@coroutineScope Pair(byDistanceCountTask.result.count - inFrontByName, distance)
        }
    }

    fun onUserCreate(email: String) {
        // Testing for success is skipped for the sake of simplicity.
        val distance = hashMapOf<String, Any>(FIELD_DISTANCE to 0L)
        collectionRef.document(email).set(distance)
    }
}