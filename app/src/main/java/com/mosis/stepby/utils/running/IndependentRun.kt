package com.mosis.stepby.utils.running

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.Timestamp
import com.mosis.stepby.utils.RunInfoKeys
import com.mosis.stepby.utils.durationToString
import org.osmdroid.util.GeoPoint

open class IndependentRun() {
    private val _currentStatus = MutableLiveData<RunStatus>(RunStatus.NOT_SET)
    val currentStatus: LiveData<RunStatus> get() = _currentStatus
    private lateinit var tsStart: Timestamp
    private lateinit var tsEnd: Timestamp
    private var _currentDistance: Double = 0.0 // in meters
    val currentDistance: Double get() = _currentDistance
    private val _path = mutableListOf<GeoPoint>()
    val path: List<GeoPoint> get() = _path.toList()

    fun start(point: GeoPoint?) {
        if (point == null) throw RunException("Starting point not set.")
        when(_currentStatus.value!!) {
            RunStatus.NOT_SET -> { _path.add(point!!); tsStart = Timestamp.now(); _currentStatus.postValue(RunStatus.IN_PROGRESS) }
            RunStatus.IN_PROGRESS -> { throw RunException("Run in progress.")}
            RunStatus.FINISHED -> { throw RunException("Run already finished.")}
        }
    }
    fun stop() {
        when(_currentStatus.value!!) {
            RunStatus.NOT_SET -> { throw RunException("Run not started.")}
            RunStatus.IN_PROGRESS -> {tsEnd = Timestamp.now(); _currentStatus.postValue(RunStatus.FINISHED)}
            RunStatus.FINISHED -> { throw RunException("Run already finished.")}
        }
    }
    open fun setCurrentPosition(point: GeoPoint) {
        when(_currentStatus.value!!) {
            RunStatus.NOT_SET -> { throw RunException("Run not started.")}
            RunStatus.IN_PROGRESS -> {val lastPoint = _path.last(); _currentDistance += lastPoint.distanceToAsDouble(point); _path.add(point)}
            RunStatus.FINISHED -> { throw RunException("Run already finished.")}
        }
    }
    fun getDurationFormatted(): String {
        return when(_currentStatus.value!!) {
            RunStatus.FINISHED -> { durationToString(tsStart, tsEnd)}
            RunStatus.IN_PROGRESS -> { durationToString(tsStart)}
            RunStatus.NOT_SET -> { String.format("--:--:--")}
        }
    }

    fun getDurationInSeconds(): Long {
        return when(currentStatus.value!!) {
            RunStatus.NOT_SET -> throw RunException("Run not started")
            RunStatus.IN_PROGRESS -> Timestamp.now().seconds - tsStart.seconds
            RunStatus.FINISHED -> tsEnd.seconds - tsStart.seconds
        }
    }

    open fun formatForUpload(name: String?, creatorEmail: String): HashMap<String, Any> {
        if (currentStatus.value!! != RunStatus.FINISHED) throw RunException("Can't upload unfinished run.")

        return hashMapOf(
            RunInfoKeys.DURATION to tsEnd.seconds - tsStart.seconds,
            RunInfoKeys.DISTANCE to currentDistance.toLong(),
            RunInfoKeys.FINISHED_AT to Timestamp.now(),
            RunInfoKeys.POINTS to path,
            RunInfoKeys.NAME to if (name.isNullOrBlank()) DEFAULT_RUN_NAME else name,
            RunInfoKeys.CREATOR_EMAIL to creatorEmail,
            RunInfoKeys.RAN_ON_TRACK to false
        )
    }

    companion object {
        const val TAG = "IndependentRun"
        private const val DEFAULT_RUN_NAME = "My run"

        private object PointKeys {
            const val LATITUDE = "latitude"
            const val LONGITUDE = "longitude"
        }

        fun mapListToPoints(mapList: List<HashMap<String, Any>>): List<GeoPoint> {
            val points = mutableListOf<GeoPoint>()
            mapList.forEach { map -> points.add(GeoPoint(map[PointKeys.LATITUDE] as Double, map[PointKeys.LONGITUDE] as Double)) }
            return points
        }
    }
}

enum class RunStatus { NOT_SET, IN_PROGRESS, FINISHED }

class RunException(msg: String): Exception() {
    override val message: String = msg
}