package com.mosis.stepby.utils.running.osmdroid

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.mosis.stepby.utils.running.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class TrackMarkerManager(private val mapView: MapView, tracks: List<Track>, flag: Drawable, onClickAction: (Track) -> Unit): AutoCloseable {

    private val markers = mutableListOf<TrackMarker>()
    private val _selected = MutableLiveData<TrackMarker>()
    val selected: LiveData<TrackMarker> get() = _selected

    private val clickCH = Channel<TrackMarker>(Channel.CONFLATED)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val markerOnClickListener: Marker.OnMarkerClickListener =
        Marker.OnMarkerClickListener { marker, _ ->
            if (marker is TrackMarker) {
                coroutineScope.launch { clickCH.send(marker) }
                onClickAction(marker.track)
                Log.d(TAG, "marker is TrackMarker")
                true
            } else {
                Log.d(TAG, "marker is not TrackMarker"); false
            }
        }

    init {
        Log.d(TAG, "init")
        for (track in tracks) {
            val marker = TrackMarker(mapView, track, flag)
            markers.add(marker)
            marker.setOnMarkerClickListener(markerOnClickListener)
        }
        mapView.overlays.addAll(markers)
        mapView.invalidate()

        coroutineScope.launch { markerOnClickLogic() }
    }

    fun clear() {
        Log.d(TAG, "clear")
        coroutineScope.cancel()
        _selected.value?.hide()
        _selected.value = null
        mapView.overlays.removeAll(markers)
        mapView.invalidate()
    }

    override fun close() {
        Log.d(TAG, "Auto Close")
        coroutineScope.cancel()
    }

    private suspend fun markerOnClickLogic() {
        while(true) {
            val marker = clickCH.receive()
            withContext(Dispatchers.Main) {
                when (_selected.value) {
                    null -> { marker.show(); _selected.value = marker }
                    marker -> { marker.hide(); _selected.value = null }
                    else -> { _selected.value!!.hide(); marker.show(); _selected.value = marker }
                }
            }
        }
    }

    companion object {
        const val TAG = "TrackMarkerManager"
    }
}