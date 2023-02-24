package com.mosis.stepby.utils

object UserInfoKeys {
    const val USERNAME = "username"
    const val FULL_NAME = "fullName"
    const val PHONE ="phone"
    const val PROFILE_PICTURE = "profilePicture"
    const val FRIENDS = "friends"
}

object UserLocationKeys {
    const val POINT = "point"
    const val LAST_SEEN = "lastSeen"
}

object FirestoreCollections {
    const val USERNAMES = "usernames"
    const val USERS = "users"
    const val USER_LOCATIONS = "userLocations"
    const val RUNS = "runs"
    const val TRACKS = "tracks"
}

object StorageFolders {
    const val IMAGES = "images"
}

object RunInfoKeys {
    const val DURATION = "duration" // in seconds
    const val DISTANCE = "distance" // in meters
    const val POINTS = "points" // ordered
    const val NAME = "name"
    const val CREATOR_EMAIL = "creatorEmail"
    const val FINISHED_AT = "finishedAt"
    const val RAN_ON_TRACK = "runOnTrack"
    const val TRACK_ID = "trackID"
}

object TrackKeys {
    const val NAME = "name"
    const val POINTS = "points"
    const val CREATOR_EMAIL = "creatorEmail"
    const val FLOORING = "flooring"
    const val RUNS = "runs"
}