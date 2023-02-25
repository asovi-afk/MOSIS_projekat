package com.mosis.stepby.utils.running

import com.mosis.stepby.utils.RunInfoKeys
import org.osmdroid.util.GeoPoint

class TrackRun(private val track: Track): IndependentRun() {
    private val _pathToRun: MutableList<GeoPoint> = track.path.toMutableList()
    val pathToRun: List<GeoPoint> get() = _pathToRun
    val finishPoint: GeoPoint get() = track.path.last()
    val trackID: String get() = track.id
    private val _trackRanPath = mutableListOf<GeoPoint>()
    val trackRanPath: List<GeoPoint> get() = _trackRanPath

    // Probably should be optimized not to run in main thread.
    override fun setCurrentPosition(point: GeoPoint) {
        super.setCurrentPosition(point)
        val iter = _pathToRun.iterator()
        val trimPath = mutableListOf<GeoPoint>()
        var touched = true

        while (touched && iter.hasNext()) {
            val p = iter.next()
            touched = p.distanceToAsDouble(point) < PROXIMITY
            if (touched) trimPath.add(p)
        }

        if (trimPath.isNotEmpty()) {
            // There are changes

            // Discard all passed points
            _pathToRun.removeAll(trimPath)

            // Adding all passed points
            if (_trackRanPath.isEmpty()) _trackRanPath.addAll(trimPath)
            // Adding all passed points except first one, which was added last time to join paths
            else { trimPath.removeFirst(); _trackRanPath.addAll(trimPath) }

            // Run is finished
            if (_pathToRun.isEmpty()) super.stop()
            // Run is not finished, so we add point to join two paths
            else _trackRanPath.add(_pathToRun.first())
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
        private const val PROXIMITY = 15.0 // in meters

        fun formatIndependentRunForUpload(run: IndependentRun, name: String?, trackID: String, creatorEmail: String): HashMap<String, Any> {
            val formatted = run.formatForUpload(name, creatorEmail)
            formatted[RunInfoKeys.RAN_ON_TRACK] = true
            formatted[RunInfoKeys.TRACK_ID] = trackID

            return formatted
        }

        fun canStartRun(track: Track, startingPoint: GeoPoint?): Boolean {
            if (startingPoint == null ) throw RunException("Starting point not set.")
            val distance = track.path.first().distanceToAsDouble(startingPoint)
            return distance < PROXIMITY
        }
    }
}