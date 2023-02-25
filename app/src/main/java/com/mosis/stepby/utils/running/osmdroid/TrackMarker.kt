package com.mosis.stepby.utils.running.osmdroid

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.service.autofill.OnClickAction
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.mosis.stepby.utils.running.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Marker.OnMarkerClickListener
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow

class TrackMarker(val map: MapView, val track: Track, flag: Drawable): Marker(map) {

    val line: Polyline

    init {
        line = Polyline(map, true)
        line.setPoints(track.path)
        line.setOnClickListener { _, _, _ -> false }

        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        position = track.path.first()
        title = track.name
        snippet = String.format("%s\n%s\n%s", track.creatorEmail, track.flooring.name, track.id)
        icon = flag
        infoWindow
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        super.onDestroy()
    }

    private fun showTrack() { map.overlays.add(line); map.invalidate()}
    private fun hideTrack() { map.overlays.remove(line); map.invalidate()}

    fun show() { showTrack() }
    fun hide() { hideTrack() }

    companion object {
        private const val TAG = "TrackMarker"
    }

}


