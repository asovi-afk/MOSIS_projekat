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
}

object StorageFolders {
    const val IMAGES = "images"
}

object RunKeys {
    const val DURATION = "duration" // in seconds
    const val DISTANCE = "distance" // in meters
    const val POINTS = "points" // ordered
    const val NAME = "name"
    const val FINISHED_AT = "finishedAt"
}