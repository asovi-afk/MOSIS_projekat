package com.mosis.stepby.utils.running

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.Timestamp
import com.mosis.stepby.utils.durationToString
import org.osmdroid.util.GeoPoint

class IndependentRun() {
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
    fun setCurrentPosition(point: GeoPoint) {
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
        if (currentStatus.value!! != RunStatus.FINISHED) throw RunException("Run not finished.")
        return tsEnd.seconds - tsStart.seconds
    }

    companion object {
        const val TAG = "IndependentRun"

    }
}

enum class RunStatus { NOT_SET, IN_PROGRESS, FINISHED }

class RunException(msg: String): Exception() {
    override val message: String = msg
}