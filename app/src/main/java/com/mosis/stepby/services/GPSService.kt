package com.mosis.stepby.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.util.TimeFormatException
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.mosis.stepby.R
import com.mosis.stepby.utils.FirestoreCollections
import com.mosis.stepby.utils.UserLocationKeys
import org.osmdroid.util.GeoPoint
import java.time.LocalDateTime
import java.util.Date


class GPSService: Service() {

    var currentPosition = MutableLiveData<GeoPoint>()

    private var currentUserEmail: String? = null

    private var serviceRunningInForeground = false
    private val activityBinder = GPSBinder()

    private lateinit var locationListener: LocationListener
    private lateinit var locationManager: LocationManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")
        registerLocationListener()

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
        locationManager.removeUpdates(locationListener)
        // Remove position from server
        if (currentUserEmail != null)
            Firebase.firestore.collection(FirestoreCollections.USER_LOCATIONS).document(currentUserEmail!!).delete()
        if (serviceRunningInForeground) stopForeground(true)
        super.onDestroy()
    }

    fun setUserEmail(email: String) {
        currentUserEmail = email
    }


    private fun registerLocationListener() {

        // Assumption: All necessary permissions are granted
        Log.d(TAG, "registerLocationListener()")


        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationListener = object: LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(TAG, "Location changed")
                currentPosition.value = GeoPoint(location.latitude, location.longitude)

                if(currentUserEmail != null) {
                    val entry = hashMapOf(
                        Pair<String, com.google.firebase.firestore.GeoPoint>(UserLocationKeys.POINT, com.google.firebase.firestore.GeoPoint(location.latitude, location.longitude)),
                        Pair<String, Timestamp>(UserLocationKeys.LAST_SEEN, Timestamp.now())
                    )
                    Firebase.firestore.collection(FirestoreCollections.USER_LOCATIONS).document(currentUserEmail!!)
                        .set(entry)
                }
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
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 5.0f, locationListener )
    }

    private fun generateNotification(): Notification {
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, "Stepby", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Stepby GPS service notification channel"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_round)
            .setContentTitle(resources.getString(R.string.app_name))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(true)

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
    }

}