package org.webrtsp.monitor.onvif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.eventFlow
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.webrtsp.monitor.MainActivity
import org.webrtsp.monitor.MotionEventRepository
import org.webrtsp.monitor.R
import org.webrtsp.monitor.Source
import org.webrtsp.monitor.SourceId
import org.webrtsp.monitor.maybeOnvif
import org.webrtsp.monitor.onvif
import org.webrtsp.monitor.toOrigin
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

data class EventSource(
    val id: SourceId,
    val endpoint: Uri,
    val userName: String?,
    val password: String?,
)

@AndroidEntryPoint
class ONVIFEventTrackerService: LifecycleService() {
    companion object {
        const val TAG = "ONVIFEventTrackerService"

        private const val NOTIFICATION_ID = 1000
        private const val NOTIFICATION_CHANEL_ID = "ONVIF Event Tracker Service Notification channel"

        private const val START_SERVICE_ACTION = "start"
        private const val START_ONVIF_ID_EXTRA = "id"
        private const val START_ONVIF_ENDPOINT_EXTRA = "endpoint"
        private const val START_USER_NAME_EXTRA = "user name"
        private const val START_PASSWORD_EXTRA = "password"

        const val STOP_SERVICE_ACTION = "stop"

        private var _guard = Mutex()
        private var _started = false

        suspend fun startTracking(
            applicationContext: Context,
            source: Source,
        ) {
            require(source.onvif || source.maybeOnvif)

            _guard.withLock {
                _started = true

                val intent = Intent(
                    applicationContext,
                    ONVIFEventTrackerService::class.java
                ).apply {
                    action = START_SERVICE_ACTION
                    with(source) {
                        putExtra(START_ONVIF_ID_EXTRA, id)
                        putExtra(START_ONVIF_ENDPOINT_EXTRA, url.toString())
                        putExtra(START_USER_NAME_EXTRA, userName)
                        putExtra(START_PASSWORD_EXTRA, password)
                    }
                }

                applicationContext.startForegroundService(intent)
            }
        }

        private fun stopIntent(applicationContext: Context): Intent {
            return Intent(
                applicationContext,
                ONVIFEventTrackerService::class.java
            ).apply { action = STOP_SERVICE_ACTION }
        }

        suspend fun stopTracking(applicationContext: Context) {
            _guard.withLock {
                if(_started) {
                    _started = false

                    applicationContext.startForegroundService(
                        stopIntent(applicationContext))
                }
            }
        }
    }

    @Inject
    lateinit var eventTrackingRepository: MotionEventRepository

    private val _notificationManager by lazy {  NotificationManagerCompat.from(applicationContext) }

    private var _trackJob: Job? = null
    private val _eventSourceFlow = MutableStateFlow<EventSource?>(null)

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startTracking(eventSource: EventSource) {
        if(_trackJob == null) {
            _trackJob = lifecycleScope.launch {
                combine(
                    ProcessLifecycleOwner.get().lifecycle.eventFlow,
                    _eventSourceFlow
                ) { processState, eventSource ->
                    when(processState) {
                        Lifecycle.Event.ON_CREATE,
                        Lifecycle.Event.ON_START,
                        Lifecycle.Event.ON_RESUME -> null
                        Lifecycle.Event.ON_PAUSE,
                        Lifecycle.Event.ON_STOP -> eventSource
                        Lifecycle.Event.ON_DESTROY,
                        Lifecycle.Event.ON_ANY -> null
                    }
                }
                .collectLatest { eventSource ->
                    eventSource?.apply {
                        ONVIFEventsChecker(
                            endpoint,
                            userName,
                            password
                        ).use { checker ->
                            checker.motionDetectedFlow
                                .onEach {
                                    eventTrackingRepository.emitMotionDetected(endpoint.toOrigin())
                                }
                                .launchIn(this@launch)
                            while(true) {
                                val state = checker.state.first { state ->
                                    state == ONVIFEventsChecker.State.Idle ||
                                    state == ONVIFEventsChecker.State.Error
                                }
                                if(state == ONVIFEventsChecker.State.Error) {
                                    delay(5.seconds)
                                } else {
                                    delay(1.seconds)
                                }
                                checker.checkEvents()
                            }
                        }
                    }
                }
            }
        }

        _eventSourceFlow.value = eventSource
    }
    private fun stopTracking(startId: Int) {
        _trackJob?.cancel()
        _trackJob = null
        stopSelf(startId)
    }
    private fun stopTracking() {
        _trackJob?.cancel()
        _trackJob = null
        stopSelf()
    }

    private fun createNotificationChannel() {
        if(_notificationManager.getNotificationChannel(NOTIFICATION_CHANEL_ID) != null)
            return

        val channel = NotificationChannel(
            NOTIFICATION_CHANEL_ID,
            getString(R.string.onvif_events_checker_notification_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
        }
        _notificationManager.createNotificationChannel(channel)
    }

    private fun startForeground(eventSource: EventSource?) {
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANEL_ID)
            .setSmallIcon(R.drawable.videocam)
            .setContentTitle(getString(R.string.onvif_events_checker_notification_title))
            .apply {
                if(eventSource != null)
                    setContentText(eventSource.endpoint.toOrigin())
            }
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
                    getString(R.string.stop_motion_tracking_notification_action),
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
                val extras = intent.extras
                val id = extras?.getLong(START_ONVIF_ID_EXTRA)
                val endpoint = extras?.getString(START_ONVIF_ENDPOINT_EXTRA)?.toUri()
                if(extras == null || id == null || endpoint == null) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                val eventSource = EventSource(
                    id,
                    endpoint,
                    extras.getString(START_USER_NAME_EXTRA),
                    extras.getString(START_PASSWORD_EXTRA))
                startForeground(eventSource)
                startTracking(eventSource)
            }
            STOP_SERVICE_ACTION -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopTracking(startId)
            }
        }

        return START_REDELIVER_INTENT
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        stopTracking()
    }
}
