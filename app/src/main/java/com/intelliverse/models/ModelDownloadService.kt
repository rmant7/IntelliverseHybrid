package com.intelliverse.models

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.intelliverse.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps model downloads alive while the app is backgrounded. Confirmed
 * necessary on a real device: without a foreground service, all three
 * catalog downloads failed the moment the app was switched away from (to
 * check a browser) -- not a slow network, Android simply has no reason to
 * protect a plain background coroutine's open socket otherwise. A
 * foreground service with a visible, ongoing notification is the standard,
 * OS-supported way to keep a long-running network transfer alive while the
 * app isn't in the foreground.
 *
 * Started via [ModelDownloads]' own onDownloadStarted hook (wired in
 * [ModelsModule]), not from the UI directly -- so it starts regardless of
 * which screen kicked off a download, and stops itself once every download
 * this process knows about has left the Running/Resolving state.
 */
@AndroidEntryPoint
class ModelDownloadService : Service() {

    @Inject lateinit var downloads: ModelDownloads

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val notification = buildNotification("Downloading models…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            downloads.states.collect { states ->
                val active = states.values.count { it is DownloadState.Running || it is DownloadState.Resolving }
                if (active == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, buildNotification("Downloading $active model(s)…"))
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Intelliverse")
            .setContentText(text)
            .setSmallIcon(com.intelliverse.R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID = 4201

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ModelDownloadService::class.java))
        }
    }
}
