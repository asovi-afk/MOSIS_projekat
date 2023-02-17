package com.mosis.stepby

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.databinding.FragmentHomeBinding
import com.mosis.stepby.services.GPSService
import com.mosis.stepby.viewmodels.HomeFragmentViewModel
import com.mosis.stepby.viewmodels.MainActivityViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.osmdroid.config.Configuration.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class HomeFragment : Fragment() {

    private val mainVM: MainActivityViewModel by activityViewModels()
    private lateinit var binding: FragmentHomeBinding

    private lateinit var markerCurrentPosition: Marker

    private lateinit var currentPositionObserver: Observer<GeoPoint>

    private lateinit var connection: ServiceConnection
    private lateinit var service: GPSService
    private var serviceValid = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate()")

        val activityManager = activity?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningServices = activityManager.getRunningServices(Int.MAX_VALUE)

        if (! (runningServices.map { it.service.className }.contains(GPSService::class.java.name)))
            Intent(context, GPSService::class.java).also { intent ->
                activity?.startService(intent) // so service won't get destroyed when nothing binds to it
            }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mainVM.showBNV.value = true
        initConnection()
        getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID)
        getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context));

        binding = FragmentHomeBinding.inflate(inflater, container, false)

        markerCurrentPosition = Marker(binding.map)
        markerCurrentPosition.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        markerCurrentPosition.icon = ContextCompat.getDrawable(context!!, R.drawable.ic_current_position_marker_24)
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.controller.setZoom(17.0)
        binding.map.controller.setCenter(GeoPoint(43.32472, 21.90333))


        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!arePermissionsGranted()) {
            Toast.makeText(context, "Permissions not granted.", Toast.LENGTH_SHORT).show()
            activity?.finish()
        }
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
    }

    override fun onPause() {
        Log.d(TAG, "onPause()")
        binding.map.onPause()
        if (serviceValid) {
            service.currentPosition.removeObserver(currentPositionObserver)
            activity!!.unbindService(connection)
            serviceValid = false
        }

        super.onPause()
    }

    private fun initConnection() {
        connection = object: ServiceConnection {
            @SuppressLint("FragmentLiveDataObserve")
            override fun onServiceConnected(className: ComponentName?, ibinder: IBinder) {
                val binder = ibinder as GPSService.GPSBinder
                service = binder.service
                serviceValid = true
                currentPositionObserver = Observer { point ->
                    binding.map.run {
                        controller.setCenter(point)
                        Log.d(TAG, "point: long: ${point.longitude}, latit: ${point.latitude}")
                        markerCurrentPosition.position = point
                        overlays.add(markerCurrentPosition)
                        invalidate()
                    }
                }
                service.currentPosition.observe(viewLifecycleOwner, currentPositionObserver)
                service.setUserEmail(Firebase.auth.currentUser?.email!!)
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                Log.d(TAG, "onServiceDisconnected")
                // For some reason this code won't be called when minimizing activity with this home fragment on top of fragmentStack. All other combinations work as intended.
                // Therefore, this line of code is directly called from within onPause() method too.

                // It should also be invoked here in the event that the service is stopped. ( Signing out )
                service.currentPosition.removeObserver(currentPositionObserver)
            }
        }
    }

    private fun arePermissionsGranted(): Boolean {
        return permissionTags.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        const val TAG = "HomeFragment"

        val permissionTags = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )

    }
}