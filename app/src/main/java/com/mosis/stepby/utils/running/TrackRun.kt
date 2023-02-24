package com.mosis.stepby.utils.running

import com.mosis.stepby.utils.RunInfoKeys
import org.osmdroid.util.GeoPoint

class TrackRun(private val track: Track): IndependentRun() {
    private val _pathToRun: MutableList<GeoPoint> = track.path.toMutableList()
    val pathToRun: List<GeoPoint> get() = _pathToRun

    override fun setCurrentPosition(point: GeoPoint) {
        super.setCurrentPosition(point)
        val nextPoint = _pathToRun.first()
        if (point.distanceToAsDouble(nextPoint) < PROXIMITY) {
            _pathToRun.remove(nextPoint)
            if (_pathToRun.isEmpty()) { super.stop() }
        }
    }

    override fun formatForUpload(name: String?, creatorEmail: String): HashMap<String, Any> {
        val formatted = super.formatForUpload(name, creatorEmail)
        formatted[RunInfoKeys.RAN_ON_TRACK] = true
        formatted[RunInfoKeys.TRACK_ID] = track.id

        return formatted
    }

    companion object {
        const val TAG = "TrackRun"
        private const val PROXIMITY = 2.0 // in meters

        fun formatIndependentRunForUpload(run: IndependentRun, name: String?, trackID: String, creatorEmail: String): HashMap<String, Any> {
            val formatted = run.formatForUpload(name, creatorEmail)
            formatted[RunInfoKeys.RAN_ON_TRACK] = true
            formatted[RunInfoKeys.TRACK_ID] = trackID

            return formatted
        }
    }
}