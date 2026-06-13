package com.example.projectammu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService

class AirGestureForegroundService : LifecycleService() {
    private var controller: AirGestureController? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, buildNotification("Watching for hand gestures"))
        startGestureCamera()
        return START_STICKY
    }

    override fun onDestroy() {
        controller?.stop()
        controller = null
        super.onDestroy()
    }

    private fun startGestureCamera() {
        if (controller != null) {
            return
        }

        controller = AirGestureController(
            context = this,
            lifecycleOwner = this,
            previewView = null,
            onGesture = ::handleGesture,
            onStatus = { status ->
                updateNotification(status)
            }
        ).also { it.start() }
    }

    private fun handleGesture(gesture: AirGesture) {
        when (gesture) {
            AirGesture.SwipeUp -> sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_SCROLL_DOWN)
            AirGesture.SwipeDown -> sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_SCROLL_UP)
            AirGesture.SwipeLeft -> sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_BACK)
            AirGesture.SwipeRight -> sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_RECENTS)
            AirGesture.OpenPalm -> dispatchMediaPlayPause()
            AirGesture.ClosedFist -> sendAccessibilityCommand(AmmuAccessibilityService.COMMAND_HOME)
        }
    }

    private fun sendAccessibilityCommand(command: String) {
        if (AmmuAccessibilityService.performIfConnected(command)) {
            return
        }

        val intent = Intent(AmmuAccessibilityService.ACTION_ACCESSIBILITY_COMMAND)
            .setPackage(packageName)
            .putExtra(AmmuAccessibilityService.EXTRA_COMMAND, command)

        sendBroadcast(intent)
    }

    private fun dispatchMediaPlayPause() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        audioManager.dispatchMediaKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        )
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        )
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: String): Notification {
        ensureNotificationChannel()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Ammu Air Gestures")
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ammu Air Gestures",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "ammu_air_gesture_channel"
        private const val NOTIFICATION_ID = 2002
    }
}
