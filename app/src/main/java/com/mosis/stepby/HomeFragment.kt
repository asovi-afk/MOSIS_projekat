package com.mosis.stepby

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.databinding.FragmentHomeBinding
import com.mosis.stepby.services.GPSService
import com.mosis.stepby.utils.OtherUserInfo
import com.mosis.stepby.utils.distanceToString
import com.mosis.stepby.utils.durationToString
import com.mosis.stepby.utils.running.*
import com.mosis.stepby.utils.running.osmdroid.TrackMarkerManager
import com.mosis.stepby.viewmodels.HomeFragmentViewModel
import com.mosis.stepby.viewmodels.MainActivityViewModel
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.advancedpolyline.ColorHelper
import java.util.*

class HomeFragment : Fragment() {

    private val mainVM: MainActivityViewModel by activityViewModels()
    private val viewModel: HomeFragmentViewModel by activityViewModels()
    private lateinit var binding: FragmentHomeBinding

    private lateinit var markerCurrentPosition: Marker
    private lateinit var userPositionsObserver: Observer<List<OtherUserInfo>>
    private lateinit var currentPositionObserver: Observer<GeoPoint>
    private lateinit var showOtherUsersObserver: Observer<Boolean>
    private lateinit var runStatusObserver: Observer<RunStatus>
    private lateinit var showTracksObserver: Observer<List<Track>>
    private lateinit var trackInfoObserver: Observer<Track>
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private lateinit var connection: ServiceConnection
    private lateinit var service: GPSService
    private var serviceValid = false
    private var timer: Timer? = null

    private var lastUsersMarkerList = listOf<Marker>()
    private lateinit var currentRunPath: Polyline
    private lateinit var currentTrackPathToRun: Polyline
    private lateinit var currentTrackRanPath: Polyline
    private lateinit var finishMarker: Marker

    private var trackManager: TrackMarkerManager? = null

    private val createTrack = MutableLiveData<Boolean>(false)
    private val filterShown = MutableLiveData<Boolean>(false)
    private val trackInfo = MutableLiveData<Track>()

    private lateinit var flag: Drawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate()")
        mainVM.loggedIn.value = true
        mainVM.signOut.value = false
        mainVM.showBNV.value = true

        generateObservers()
        initConnection()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView")

        flag = BitmapDrawable(resources, getFlagBitmap())

        getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID)
        getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context));

        // Polyline object gets destroyed in fragments onDestroyView()
        currentRunPath = Polyline()
        currentTrackPathToRun = Polyline()
        currentTrackRanPath = Polyline()

        binding = FragmentHomeBinding.inflate(inflater, container, false)
        markerCurrentPosition = Marker(binding.map)
        markerCurrentPosition.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        markerCurrentPosition.icon = ContextCompat.getDrawable(context!!, R.drawable.ic_current_position_marker_24)
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.controller.setZoom(19.0)
        binding.map.controller.setCenter(GeoPoint(43.32472, 21.90333))
        // Marker is used when currentTrackPathToRun has only one GeoPoint left.
        // Polyline with one GeoPoint is not shown on map.
        finishMarker = Marker(binding.map)
        finishMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        finishMarker.icon = ContextCompat.getDrawable(context!!, R.drawable.ic_baseline_finish_24)


        binding.swcShowOtherUsers.setOnClickListener { viewModel.showOtherUsers.value = !(viewModel.showOtherUsers.value!!) }

        binding.ivStartRun.setOnClickListener {
            try {
                if (serviceValid) {
                    if (trackInfo.value == null)
                        service.activeRun.start(service.currentPosition.value)
                    else if (TrackRun.canStartRun(trackInfo.value!!, service.currentPosition.value)) {
                        createNewTrackRun(trackInfo.value!!)
                        service.activeRun.start(service.currentPosition.value)
                    } else
                        Toast.makeText(context, "Get closer to start.", Toast.LENGTH_SHORT).show()
                } else
                    Toast.makeText(context, "Location service unavailable.", Toast.LENGTH_SHORT).show()
            } catch(e: RunException) { toastMessage(e.message) }
        }
        binding.ivStopRun.setOnClickListener { try { if (serviceValid) service.activeRun.stop() } catch(e: RunException) {toastMessage(e.message)}}
        binding.ivDeleteRun.setOnClickListener { if (serviceValid) createNewIndependentRun() }
        binding.ivSaveRun.setOnClickListener {
            if (serviceValid) {
                val run = service.activeRun
                val runName = binding.editRunName.text.toString()
                if (createTrack.value!!) {
                    val trackName = binding.editTrackName.text.toString()
                    val flooring = getFlooring()
                    viewModel.uploadRunWithTrack(run, runName, trackName, flooring, run.currentDistance.toLong())
                }
                else if (service.followsTrack) viewModel.uploadTrackRun(run as TrackRun, runName)
                else viewModel.uploadRun(run, runName)
                createNewIndependentRun()
            }
        }
        binding.tvPotentialTrack.setOnClickListener { createTrack.value = !createTrack.value!!}
        binding.ivFilterTrack.setOnClickListener { filterShown.value = !filterShown.value!! }
        binding.sbFilterRadius.run {
            valueTo = TrackFilter.RADIUS_ALL.toFloat()
            valueFrom = TrackFilter.RADIUS_NONE.toFloat()
            stepSize = (valueTo - valueFrom) / 50.0f
            addOnChangeListener { _, value, _ -> binding.tvFilterRadiusValue.text = formatRadiusValue(value) }
            values = mutableListOf(TrackFilter.RADIUS_ALL.toFloat())
        }

        // Important note on why we are adding observers in onCreate and not in onResume
        // 1. If we were to add them in onResume, we would be held accountable to remove them in onPause.
        // 2. Observer will only receive events when 'viewLifeCycleOwner' is in states Start or Resume.
        // 3. 'viewLifeCycleOwner' will get destroyed just before onDestroyView which will happen every time navigation graph navigates away from this fragment,
        //      but will not happen when activity calls onStop while this fragment is on display (when activity is no longer visible; example: pressing home button).
        //      And when 'viewLifeCycleOwner' gets destroyed, LiveData will remove all references to this owner and its observers.
        // 4. After navigating to this fragment (whether or not it was on backstack) this onCreateView will be called and initiate observing.
        // 5. reference activity and fragment life cycles
        viewModel.otherUsersChanges.observe(viewLifecycleOwner, userPositionsObserver)
        viewModel.showOtherUsers.observe(viewLifecycleOwner, showOtherUsersObserver)
        viewModel.instantToast.observe(viewLifecycleOwner) { msg -> if (!msg.isNullOrBlank()) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

        createTrack.observe(viewLifecycleOwner) {
            if (it) { binding.llTrackCreation.visibility = View.VISIBLE; binding.tvPotentialTrack.text = getString(R.string.cancel_create_track)}
            else { binding.llTrackCreation.visibility = View.GONE; binding.tvPotentialTrack.text = getString(R.string.create_track) }
        }
        filterShown.observe(viewLifecycleOwner) {
            if (it) binding.llTrackFilter.visibility = View.VISIBLE
            else { binding.llTrackFilter.visibility = View.GONE; setTrackFilter() }
        }
        trackInfo.observe(viewLifecycleOwner, trackInfoObserver)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume()")
        if (!serviceValid) {
            Intent(context, GPSService::class.java).also { intent ->
                activity?.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        }
        binding.map.onResume()
        viewModel.startListeningForLocationChanges()
    }

    override fun onPause() {
        Log.d(TAG, "onPause()")
        // Although it could be done in onDestroyView, it's probably better to free resources the moment they are no longer needed.
        timer?.cancel() // Just in case.
        binding.map.onPause()
        if (serviceValid) {
            service.currentPosition.removeObserver(currentPositionObserver)
            service.activeRun.currentStatus.removeObserver(runStatusObserver)
            service.visibleTracks.removeObserver(showTracksObserver)
            activity!!.unbindService(connection)
            serviceValid = false
        }

        viewModel.stopListeningForLocationChanges()
        super.onPause()
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView()")
        trackManager?.clear()
        viewModel.clearInstantToast()
        super.onDestroyView()
    }

    private fun initConnection() {
        connection = object: ServiceConnection {
            @SuppressLint("FragmentLiveDataObserve")
            override fun onServiceConnected(className: ComponentName?, ibinder: IBinder) {
                val binder = ibinder as GPSService.GPSBinder
                service = binder.service
                serviceValid = true

                setTrackFilter()

                service.currentPosition.observe(viewLifecycleOwner, currentPositionObserver)
                service.activeRun.currentStatus.observe(viewLifecycleOwner, runStatusObserver)
                service.visibleTracks.observe(viewLifecycleOwner, showTracksObserver)
                service.setUserEmail(Firebase.auth.currentUser?.email!!)
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                Log.d(TAG, "onServiceDisconnected")
                // NOTE: After changes to managing services I don't know if this problem persists (haven't tested it yet).
                // PROBLEM:
                // For some reason this code won't be called when minimizing activity with this home fragment on top of fragmentStack. All other combinations work as intended.
                // Therefore, this line of code is directly called from within onPause() method too.

                // It should also be invoked here in the event that the service is stopped. ( Signing out )
                service.currentPosition.removeObserver(currentPositionObserver)
                service.activeRun.currentStatus.removeObserver(runStatusObserver)
                service.visibleTracks.removeObserver(showTracksObserver)
            }
        }
    }

    private fun generateObservers() {
        currentPositionObserver = Observer { point ->
            binding.map.run {
                controller.setCenter(point)
                Log.d(TAG, "point: long: ${point.longitude}, latit: ${point.latitude}")
                if (service.activeRun.currentStatus.value == RunStatus.IN_PROGRESS){
                    updateDistance()
                    currentRunPath.addPoint(point)
                    if (service.followsTrack) {
                        val run = service.activeRun as TrackRun
                        currentTrackPathToRun.setPoints(run.pathToRun)
                        currentTrackRanPath.setPoints(run.trackRanPath)
                    }
                }
                markerCurrentPosition.position = point
                overlays.add(markerCurrentPosition)
                invalidate()
            }
        }

        userPositionsObserver = Observer { list ->
            if (viewModel.showOtherUsers.value!!) {
                val map = binding.map
                map.overlays.removeAll(lastUsersMarkerList)
                val newUsersMarkers = mutableListOf<Marker>()
                for (info in list) {
                    val marker = Marker(map)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.position = info.point
                    marker.icon = if (info.picture != null)
                        BitmapDrawable(resources, info.picture)
                    else
                        ContextCompat.getDrawable(context!!, R.drawable.ic_marker)
                    marker.setOnMarkerClickListener { _, _ ->
                        mainVM.userPreviewEmail = info.email
                        findNavController().navigate(R.id.action_homeFragment_to_otherProfileFragment)
                        true
                    }
                    map.overlays.add(marker)
                    newUsersMarkers.add(marker)
                }
                lastUsersMarkerList = newUsersMarkers
                map.invalidate()
            }
        }

        showOtherUsersObserver = Observer {
            binding.swcShowOtherUsers.isChecked = it

            if (it)
                viewModel.resendOtherUsersLocaton()
            else {
                binding.map.run {
                    overlays.removeAll(lastUsersMarkerList)
                    invalidate()
                }
            }
        }

        runStatusObserver = Observer { status ->
            binding.run {
                Log.d(TAG, "runStatus Observer")
                ivStartRun.visibility = View.GONE
                llActivity.visibility = View.GONE
                ivStopRun.visibility = View.GONE
                llActivityDone.visibility = View.GONE
                when(status!!) {
                    RunStatus.NOT_SET -> {
                        // IMPORTANT NOTE!
                        // This is default state in between any two runs.
                        // Q: Why?
                        // A: Because whenever we save, delete or finish active run unexpectedly 'createNewIndependentRun()' is called.
                        //      Thus, 'runStatusObserver' observer will be triggered for this, 'RunStatus.NOT_SET' code part.
                        createTrack.value = false
                        binding.map.overlays.remove(currentRunPath)
                        binding.map.overlays.remove(currentTrackPathToRun)
                        binding.map.overlays.remove(currentTrackRanPath)
                        binding.map.overlays.remove(finishMarker)
                        binding.map.invalidate()
                        ivStartRun.visibility = View.VISIBLE
                    }
                    RunStatus.IN_PROGRESS -> {
                        trackTime()
                        updateDistance()
                        llActivity.visibility = View.VISIBLE
                        ivStopRun.visibility = View.VISIBLE
                        setAndDrawCurrentRunPath()
                    }
                    RunStatus.FINISHED -> {
                        timer?.cancel()
                        tvDurationValue.text = service.activeRun.getDurationFormatted()
                        updateDistance()

                        // Check if run has at least one point other than starting point.
                        // It would be better to check for distance instead, but first option is debug friendly.
                        if (service.activeRun.path.size == 1) {
                            createNewIndependentRun()
                            Toast.makeText(context, "Run too short.", Toast.LENGTH_SHORT).show()
                        }
                        else if (service.followsTrack && (service.activeRun as TrackRun).pathToRun.isNotEmpty() ) {
                            createNewIndependentRun()
                            Toast.makeText(context, "Track run not finished.", Toast.LENGTH_SHORT).show()
                        }
                        else {
                            llActivity.visibility = View.VISIBLE
                            llActivityDone.visibility = View.VISIBLE
                            tvPotentialTrack.visibility = if (service.followsTrack) View.GONE else View.VISIBLE
                            setAndDrawCurrentRunPath()
                        }
                    }
                }
            }
        }

        showTracksObserver = Observer { tracks ->
            trackManager?.clear()
            trackInfo.value = null
            trackManager = TrackMarkerManager(binding.map, tracks, flag, { track -> trackInfo.value = if (trackInfo.value == track) null else track })
        }

        trackInfoObserver = Observer { track ->
            if (track == null) binding.llTrackInfo.visibility = View.GONE
            else {
                binding.run {
                    tvTrackInfoType.text = track.flooring.name
                    tvTrackInfoDistance.text = distanceToString(track.distance)
                    ivGetTrackInfo.setOnClickListener { Toast.makeText(context,"Hello There", Toast.LENGTH_SHORT).show() }
                    llTrackInfo.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun createNewIndependentRun() {
        service.activeRun.currentStatus.removeObserver(runStatusObserver)
        service.createNewIndependentRun()
        service.activeRun.currentStatus.observe(viewLifecycleOwner, runStatusObserver)
    }

    private fun createNewTrackRun(track: Track) {
        service.activeRun.currentStatus.removeObserver(runStatusObserver)
        service.createNewTrackRun(track)
        service.activeRun.currentStatus.observe(viewLifecycleOwner, runStatusObserver)
    }

    private fun setAndDrawCurrentRunPath() {
        Log.d(TAG, "setAndDrawCurrentRunPath()")
        if (service.followsTrack) {
            val run = service.activeRun as TrackRun

            binding.map.overlays.remove(currentTrackPathToRun)
            currentTrackPathToRun = Polyline()
            currentTrackPathToRun.color = TRACK_PATH_TO_RUN_COLOR
            currentTrackPathToRun.setPoints(run.pathToRun)
            binding.map.overlays.add(currentTrackPathToRun)

            binding.map.overlays.remove(currentTrackRanPath)
            currentTrackRanPath = Polyline()
            currentTrackRanPath.color = TRACK_PATH_RAN_COLOR
            currentTrackRanPath.setPoints(run.trackRanPath)
            binding.map.overlays.add(currentTrackRanPath)

            finishMarker.position = run.finishPoint
            binding.map.overlays.add(finishMarker)
        }
        binding.map.overlays.remove(currentRunPath)
        currentRunPath = Polyline()
        currentRunPath.color = PATH_RAN_COLOR
        currentRunPath.setPoints(service.activeRun.path)
        binding.map.overlays.add(currentRunPath)
        binding.map.invalidate()
    }

    private fun getFlooring(): TrackFlooring {
        return when(binding.rgTrackFlooring.checkedRadioButtonId) {
            R.id.rbAsphalt -> TrackFlooring.ASPHALT
            R.id.rbGravel -> TrackFlooring.GRAVEL
            R.id.rbOther -> TrackFlooring.OTHER
            else -> { TrackFlooring.OTHER}
        }
    }

    private fun setTrackFilter() {
        if (serviceValid) {
            binding.run {
                val radius = sbFilterRadius.values.first().toInt()
                val filter = TrackFilter(radius, swcFilterAsphalt.isChecked, swcFilterGravel.isChecked, swcFilterOther.isChecked)
                service.setTrackFilter(filter)
            }
        }
    }

    private fun formatRadiusValue(value: Float): String {
        return if (value.toInt() == TrackFilter.RADIUS_NONE) "don't show tracks"
        else if (value.toInt() == TrackFilter.RADIUS_ALL) "show all tracks"
        else value.toInt().toString() + " m"
    }

    private fun trackTime() {
        timer?.cancel()
        // after timer.cancel() it is necessary to create both, new timer and new timer task
        val timerTask = object: TimerTask() {
            override fun run() {
                coroutineScope.launch(Dispatchers.Main) {
                    try {
                        binding.tvDurationValue.text = service.activeRun.getDurationFormatted()
                    } catch(e: Exception) { Log.d(TAG, "TimerTask Error: " + e.message)}
                }
            }
        }
        timer = Timer()
        timer!!.scheduleAtFixedRate(timerTask, 0, 1000)
    }

    private fun updateDistance() {
        val currentDistance = service.activeRun.currentDistance

        if (currentDistance < 1000) {
            binding.tvDistanceValue.text = currentDistance.toInt().toString()
            binding.tvDistanceUnit.text = "m"
        }
        else {
            binding.tvDistanceValue.text = String.format("%.2f",currentDistance/1000)
            binding.tvDistanceUnit.text = "km"
        }
    }

    private fun getFlagBitmap(): Bitmap {
        val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.race_flag)
        val radius = 85.0
        val factor = radius / bitmap.height
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).toInt(), (bitmap.height * factor).toInt(), true)
    }

    private fun toastMessage(msg: String) { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }


    companion object {
        const val TAG = "HomeFragment"

        val PATH_RAN_COLOR = ColorHelper.HSLToColor(39.0f, 100.0f,50.0f)
        val TRACK_PATH_TO_RUN_COLOR = ColorHelper.HSLToColor(19.0f, 70.0f,50.0f)
        val TRACK_PATH_RAN_COLOR = ColorHelper.HSLToColor(100.0f, 120.0f,89.0f)
    }
}