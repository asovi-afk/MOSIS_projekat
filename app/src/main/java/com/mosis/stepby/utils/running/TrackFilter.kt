package com.mosis.stepby.utils.running

import android.media.MediaPlayer.TrackInfo
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.utils.FirestoreCollections
import com.mosis.stepby.utils.TrackKeys
import kotlinx.coroutines.tasks.await
import org.osmdroid.util.GeoPoint

class TrackFilter(_radius: Int, asphalt: Boolean, gravel: Boolean,other: Boolean) {

    private var flooring: List<TrackFlooring>
    private val radius = _radius
    private val firestore = Firebase.firestore

    init {
        val ml = mutableListOf<TrackFlooring>()
        if (asphalt) ml.add(TrackFlooring.ASPHALT)
        if (gravel) ml.add(TrackFlooring.GRAVEL)
        if (other) ml.add(TrackFlooring.OTHER)
        flooring = ml
    }

    suspend fun filter(center: GeoPoint): List<Track> {
        val list = mutableListOf<Track>()
        if (radius == RADIUS_NONE) return list
        val tracksSnapshot = firestore.collection(FirestoreCollections.TRACKS).get().await()
        tracksSnapshot.documents.forEach { track ->
            val points = IndependentRun.mapListToPoints(track.get(TrackKeys.POINTS) as List<HashMap<String, Any>>)
            val floor = TrackFlooring.valueOf(track.get(TrackKeys.FLOORING) as String)
            if (flooring.contains(floor) && (center.distanceToAsDouble(points.first()).toLong() <= radius || radius == RADIUS_ALL)) {
                val name = track.get(TrackKeys.NAME) as String
                val creatorEmail = track.get(TrackKeys.CREATOR_EMAIL) as String
                val distance = track.get(TrackKeys.DISTANCE) as Long
                list.add(Track(points, name, track.id, creatorEmail, floor, distance))
            }
        }
        return list
    }

    companion object {
        private const val TAG = "TrackFilter"

        const val RADIUS_ALL = 1000 // in meters
        const val RADIUS_NONE = 0 // in meters

        val FILTER_NONE = TrackFilter(RADIUS_NONE, false, false, false)
    }

}