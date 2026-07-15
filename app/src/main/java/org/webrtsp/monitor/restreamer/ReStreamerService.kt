package org.webrtsp.monitor.restreamer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.webrtsp.monitor.MainActivity
import org.webrtsp.monitor.R
import org.webrtsp.monitor.SettingsRepository
import javax.inject.Inject

@AndroidEntryPoint
class ReStreamerService: LifecycleService() {
    companion object {
        const val TAG = "ONVIFEventTrackerService"

        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANEL_ID = "ReStreamer Service Notification channel"

        private const val START_SERVICE_ACTION = "start"

        const val STOP_SERVICE_ACTION = "stop"

        private var _guard = Mutex()
        private var _started = false

        suspend fun startReStream(
            applicationContext: Context,
        ) {
            _guard.withLock {
                _started = true

                val intent = Intent(
                    applicationContext,
                    ReStreamerService::class.java
                ).apply {
                    action = START_SERVICE_ACTION
                }

                applicationContext.startForegroundService(intent)
            }
        }

        private fun stopIntent(applicationContext: Context): Intent {
            return Intent(
                applicationContext,
                ReStreamerService::class.java
            ).apply {
                action = STOP_SERVICE_ACTION
            }
        }

        suspend fun stopReStream(applicationContext: Context) {
            _guard.withLock {
                if(_started) {
                    _started = false

                    applicationContext.startForegroundService(
                        stopIntent(applicationContext))
                }
            }
        }
    }

    private val _notificationManager by lazy {  NotificationManagerCompat.from(applicationContext) }

    @Inject
    lateinit var settingsRepository: SettingsRepository
    private var _trackSourcesJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
    }

    private fun startReStream() {
        if(_trackSourcesJob == null) {
            _trackSourcesJob = lifecycleScope.launch {
                settingsRepository.allSourcesFlow.collect { sources ->
                }
            }
        }
    }
    private fun stopReStream(startId: Int) {
        _trackSourcesJob?.cancel()
        _trackSourcesJob = null
        stopSelf(startId)
    }
    private fun stopReStream() {
        _trackSourcesJob?.cancel()
        _trackSourcesJob = null
        stopSelf()
    }

    private fun createNotificationChannel() {
        if(_notificationManager.getNotificationChannel(NOTIFICATION_CHANEL_ID) != null)
            return

        val channel = NotificationChannel(
            NOTIFICATION_CHANEL_ID,
            getString(R.string.restreamer_notification_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
        }
        _notificationManager.createNotificationChannel(channel)
    }

    private fun startForeground() {
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANEL_ID)
            .setSmallIcon(R.drawable.videocam)
            .setContentTitle(getString(R.string.restreamer_notification_title))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setOngoing(false)
            .apply {
                val intent = Intent(
                    applicationContext,
                    MainActivity::class.java
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    applicationContext,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setContentIntent(pendingIntent)
            }
            .apply {
                // FIXME? replace with BroadcastReceiver target
                // to be able remove related configuration flag
                val pendingIntent = PendingIntent.getService(
                    applicationContext,
                    0,
                    stopIntent(applicationContext),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                addAction(
                    R.drawable.videocam_off,
                    getString(R.string.stop_restreamer_notification_action),
                    pendingIntent)
            }
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if(intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when(flags) {
            START_FLAG_RETRY,
            START_FLAG_REDELIVERY -> {}
        }

        when(intent.action) {
            START_SERVICE_ACTION -> {
                startForeground()
                startReStream()
            }
            STOP_SERVICE_ACTION -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopReStream(startId)
            }
        }

        return START_REDELIVER_INTENT
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        stopReStream()
    }
}
