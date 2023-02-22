package com.mosis.stepby.utils

import com.google.firebase.Timestamp

fun durationToString(start: Timestamp, end: Timestamp = Timestamp.now()): String {
    var duration = end.seconds - start.seconds
    val seconds = duration % 60
    duration /= 60
    val minutes = duration % 60
    val hours = duration / 60
    return String.format("$hours:%02d:%02d", minutes, seconds)
}