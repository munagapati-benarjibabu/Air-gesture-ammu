package com.example.projectammu

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LostPhoneTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification("Sending location email every 10 minutes"))
        startTracking()
        return START_STICKY
    }

    private fun startTracking() {
        if (trackingJob?.isActive == true) {
            return
        }

        trackingJob = serviceScope.launch {
            while (true) {
                sendCurrentLocationEmail()
                delay(LOCATION_EMAIL_INTERVAL_MS)
            }
        }
    }

    private suspend fun sendCurrentLocationEmail() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission missing")
            return
        }

        if (!SmtpEmailSender.isConfigured()) {
            Log.w(TAG, "Lost phone email SMTP settings missing")
            return
        }

        val location = getCurrentLocation()

        if (location == null) {
            Log.w(TAG, "Unable to read current location")
            return
        }

        val mapsUrl = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        val body = """
            Ammu lost phone location update

            Latitude: ${location.latitude}
            Longitude: ${location.longitude}
            Accuracy: ${location.accuracy} meters

            Open map:
            $mapsUrl
        """.trimIndent()

        runCatching {
            SmtpEmailSender.sendLostPhoneLocation(
                subject = "Ammu phone location update",
                body = body
            )
        }.onSuccess {
            Log.d(TAG, "Location email sent")
        }.onFailure { error ->
            Log.e(TAG, "Failed to send location email", error)
        }
    }

    private suspend fun getCurrentLocation(): Location? {
        val client = LocationServices.getFusedLocationProviderClient(this)
        val cancellationTokenSource = CancellationTokenSource()

        return suspendCancellableCoroutine { continuation ->
            try {
                client.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { location ->
                    continuation.resume(location)
                }.addOnFailureListener {
                    continuation.resume(null)
                }
            } catch (securityException: SecurityException) {
                continuation.resume(null)
            }

            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    private fun createNotification(contentText: String): Notification {
        createNotificationChannel()

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LostPhoneTrackingService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Ammu lost phone tracking")
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lost phone tracking",
            NotificationManager.IMPORTANCE_LOW
        )

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        trackingJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.projectammu.action.STOP_LOST_PHONE_TRACKING"
        private const val CHANNEL_ID = "lost_phone_tracking"
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "LostPhoneTracking"
        private const val LOCATION_EMAIL_INTERVAL_MS = 10 * 60 * 1000L
    }
}
