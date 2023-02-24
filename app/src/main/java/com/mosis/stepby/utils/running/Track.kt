package com.mosis.stepby.utils.running

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.StorageReference
import com.mosis.stepby.utils.FirestoreCollections
import com.mosis.stepby.utils.StorageFolders
import com.mosis.stepby.utils.TrackKeys
import com.mosis.stepby.utils.UserInfoKeys
import kotlinx.coroutines.tasks.await
import org.osmdroid.util.GeoPoint

data class Track(private val _path: List<GeoPoint>, private val _name: String, private val _id: String, private val _creatorEmail: String, private val _flooring: TrackFlooring) {
    val path: List<GeoPoint> get() = _path
    val name: String get() = _name
    val id: String get() = _id
    val creatorEmail: String get() = _creatorEmail
    val flooring: TrackFlooring get() = _flooring

    // suspend fun getRuns(): List<Pair<String, IndependentRun>> {}

    companion object {
        private const val TAG = "Track"
        private const val DEFAULT_NAME_POSTFIX = "'s fun track"

        suspend fun generateDefaultName(email: String, firestore: FirebaseFirestore): String {
            val user = firestore.collection(FirestoreCollections.USERS).document(email).get().await()
            val username = user.getString(UserInfoKeys.USERNAME)
            return username + DEFAULT_NAME_POSTFIX
        }

        fun generateDefaultName(username: String): String {
            return username + DEFAULT_NAME_POSTFIX
        }

        fun formatForUpload(path: List<GeoPoint>, creatorEmail: String, flooring: TrackFlooring, name: String): HashMap<String, Any> {
            return hashMapOf(
                TrackKeys.POINTS to path,
                TrackKeys.CREATOR_EMAIL to creatorEmail,
                TrackKeys.FLOORING to flooring.name,
                TrackKeys.NAME to name
            )
        }

        suspend fun getTrackByID(id: String, firestore: FirebaseFirestore): Track {
            val doc = firestore.collection(FirestoreCollections.TRACKS).document(id).get().await()
            if (doc.data == null || doc.data!!.isEmpty() ) throw Exception("Track ID not valid.")

            val path = doc.get(TrackKeys.POINTS) as List<GeoPoint>
            val name = doc.getString(TrackKeys.NAME)!!
            val email = doc.getString(TrackKeys.CREATOR_EMAIL)!!
            val flooring = TrackFlooring.valueOf(doc.getString(TrackKeys.FLOORING)!!)

            return Track(path, name, id, email, flooring)
        }
    }
}

enum class TrackFlooring {ASPHALT, GRAVEL, OTHER}