package com.mosis.stepby.utils

import android.graphics.Bitmap
import com.google.firebase.Timestamp
import org.osmdroid.util.GeoPoint

data class OtherUserInfo(val email: String, var point: GeoPoint, val picture: Bitmap?)

data class BasicProfileInfo(val username: String? = null, val email: String? = null, val bitmap: Bitmap? = null, val ranking: Long? = null, val distance: Long? = null) {
    constructor(bpi: BasicProfileInfo) : this(bpi.username, bpi.email, bpi.bitmap, bpi.ranking, bpi.distance)
}

data class RunInfo(val id: String, val name: String, val distance: Long, val duration: Long, val finishedTS: Timestamp, val path: List<GeoPoint>, val trackID: String?)

data class FriendInfo(val email: String, val username: String, val picture: Bitmap)

enum class ProfileTab { RUNS, TRACKS, FRIENDS}