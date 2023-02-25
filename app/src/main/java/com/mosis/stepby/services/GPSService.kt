package com.mosis.stepby.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.MainActivity
import com.mosis.stepby.R
import com.mosis.stepby.utils.FirestoreCollections
import com.mosis.stepby.utils.UserLocationKeys
import com.mosis.stepby.utils.running.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.osmdroid.util.GeoPoint
import kotlin.coroutines.coroutineContext


class GPSService: Service() {

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _currentPosition = MutableLiveData<GeoPoint>()
    val currentPosition: LiveData<GeoPoint> get() = _currentPosition
    // activeRun - Because service can run in foreground it is best suited for keeping reference.
    private var _activeRun = IndependentRun()
    val activeRun: IndependentRun get() = _activeRun
    private var _followsTrack = false
    val followsTrack: Boolean get() = _followsTrack


    private var trackFilter = TrackFilter.FILTER_NONE
    private val filterRequestCH = Channel<GeoPoint>(Channel.CONFLATED)
    private val _visibleTracks = MutableLiveData<List<Track>>(listOf())
    val visibleTracks: LiveData<List<Track>> get() = _visibleTracks

    private var currentUserEmail: String? = null
    private var serviceRunningInForeground = false
    private val activityBinder = GPSBinder()

    private lateinit var locationListener: LocationListener
    private lateinit var locationManager: LocationManager
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")
        registerLocationListener()

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Stepby", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Stepby GPS service notification channel"
        notificationManager.createNotificationChannel(channel)

        coroutineScope.launch { trackFilterLogic() }
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "onBind()")
        return activityBinder
    }

    override fun onRebind(intent: Intent) {
        Log.d(TAG, "onRebind()")
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind()")
        // Da bi se pozivala reBind
        return true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand()")

        if (intent != null) {
            if (intent.getBooleanExtra(START_FOREGROUND, false) && !serviceRunningInForeground) {
                val notification = generateNotification()
                startForeground(NOTIFICATION_ID, notification)
                serviceRunningInForeground = true
            } else if(intent.getBooleanExtra(STOP_FOREGROUND, false) && serviceRunningInForeground) { stopForeground(true); serviceRunningInForeground = false}
            val newUserEmail = intent.getStringExtra(USER_EMAIL)
            if (!newUserEmail.isNullOrBlank()) { currentUserEmail = newUserEmail }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        coroutineScope.cancel()
        locationManager.removeUpdates(locationListener)
        // Remove position from server
        if (currentUserEmail != null)
            Firebase.firestore.collection(FirestoreCollections.USER_LOCATIONS).document(currentUserEmail!!).delete()
        notificationManager.cancelAll()
        super.onDestroy()
    }

    fun setUserEmail(email: String) {
        currentUserEmail = email
    }

    fun createNewIndependentRun() {
        _activeRun = IndependentRun()
        _followsTrack = false
    }

    fun createNewTrackRun(track: Track) {
        _activeRun = TrackRun(track)
        _followsTrack = true
    }

    fun setTrackFilter(filter: TrackFilter) {
        trackFilter = filter
        if (currentPosition.value != null) requestFilter(currentPosition.value!!)
    }

    private fun requestFilter(point: GeoPoint) { coroutineScope.launch { filterRequestCH.send(point) } }

    private suspend fun trackFilterLogic() {
        while(true){
            // Don't do this:
            // val list = trackFilter.filter(filterRequestCH.receive())
            // It's unpredictable
            val center = filterRequestCH.receive()
            val list = trackFilter.filter(center)
            Log.d(TAG, "track filter logic after CH.receive()")
            val oldList = _visibleTracks.value!!

            val listsHaveSameElements = oldList.size == list.size && oldList.all { oldItem -> list.firstOrNull() { newItem -> oldItem.id == newItem.id } != null }
            if (!listsHaveSameElements) {
                _visibleTracks.postValue(list)
                if (serviceRunningInForeground && list.isNotEmpty()) {
                    notificationManager.notify(TRACKS_NOTIFICATION_ID, generateFoundTracksNotification())
                    Log.d(TAG, "track filter logic after notify")
                }
            }

        }
    }

    private fun registerLocationListener() {

        // Assumption: All necessary permissions are granted
        Log.d(TAG, "registerLocationListener()")


        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val lastLocation = locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
        if (lastLocation != null)
            _currentPosition.value = GeoPoint(lastLocation.latitude, lastLocation.longitude)

        locationListener = object: LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(TAG, "Location changed")
                val newPoint = GeoPoint(location.latitude, location.longitude)
                _currentPosition.postValue(newPoint)
                requestFilter(newPoint)

                if(currentUserEmail != null) {
                    val entry = hashMapOf(
                        Pair<String, com.google.firebase.firestore.GeoPoint>(UserLocationKeys.POINT, com.google.firebase.firestore.GeoPoint(location.latitude, location.longitude)),
                        Pair<String, Timestamp>(UserLocationKeys.LAST_SEEN, Timestamp.now())
                    )
                    Firebase.firestore.collection(FirestoreCollections.USER_LOCATIONS).document(currentUserEmail!!)
                        .set(entry)
                }
                if (_activeRun.currentStatus.value == RunStatus.IN_PROGRESS) _activeRun.setCurrentPosition(newPoint)
            }

            // So it won't break with older API versions
            // https://stackoverflow.com/questions/64638260/android-locationlistener-abstractmethoderror-on-onstatuschanged-and-onproviderd/69539440#69539440
            override fun onProviderEnabled( provider: String) {}
            override fun onProviderDisabled( provider: String) {}
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        // GPS based
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
            500L, 1.0f, locationListener)
        // Network based
        //locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 5.0f, locationListener )
    }

    private fun generateNotification(): Notification {
//        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Stepby", NotificationManager.IMPORTANCE_DEFAULT)
//        channel.description = "Stepby GPS service notification channel"

        //val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        //notificationManager.createNotificationChannel(channel)

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_round)
            .setContentTitle(resources.getString(R.string.app_name))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(true)

        return notificationBuilder.build()
    }

    private fun generateFoundTracksNotification(): Notification {
        // Notification Channel is already created when this function is called

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_round)
            .setContentTitle("Found track nearby!")
            .setContentIntent(pendingIntent)
            //.setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(false)
            .setAutoCancel(true)

        return notificationBuilder.build()
    }

    inner class GPSBinder: Binder() {
        val service: GPSService
            get() = this@GPSService
    }

    companion object {
        const val START_FOREGROUND = "START_FOREGROUND"
        const val STOP_FOREGROUND = "STOP_FOREGROUND"
        const val USER_EMAIL = "USER_EMAIL"

        private const val TAG = "GPSService"
        private const val NOTIFICATION_ID = 123456
        private const val NOTIFICATION_CHANNEL_ID = "gps_service_channel_001"
        private const val TRACKS_NOTIFICATION_ID = 1234
    }

}