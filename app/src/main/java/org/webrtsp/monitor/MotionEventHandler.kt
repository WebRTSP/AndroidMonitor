package org.webrtsp.monitor

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds


@Singleton
class MotionEventHandler @Inject constructor(
    @param:ApplicationContext private val _applicationContext: Context,
    private val _permissionsRepository: PermissionsRepository,
    private val _motionEventRepository: MotionEventRepository,
) {
    companion object {
        const val TAG = "MotionEventHandler"

        private const val NOTIFICATION_CHANEL_ID = "Motion Notification channel"
        private const val NOTIFICATION_INTERVAL = 5 // seconds
    }

    private val _notificationManager = NotificationManagerCompat.from(_applicationContext)
    private var _lastNotificationId: Int = 0

    init {
        createNotificationChannel()

        startEventHandling()
    }

    private fun createNotificationChannel() {
        if(_notificationManager.getNotificationChannel(NOTIFICATION_CHANEL_ID) != null)
            return

        val channel = NotificationChannel(
            NOTIFICATION_CHANEL_ID,
            _applicationContext.getString(R.string.motion_events_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        _notificationManager.createNotificationChannel(channel)
    }

    private fun clearOldNotifications() {
        _notificationManager.activeNotifications.filter { notification ->
            notification.notification.channelId == NOTIFICATION_CHANEL_ID
        }
        .forEach { notification ->
            _notificationManager.cancel(notification.id)
        }
    }

    private fun startEventHandling() {
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            _motionEventRepository.motionDetectedFlow
                .sample(NOTIFICATION_INTERVAL.seconds)
                .collect { sourceId ->
                    clearOldNotifications()

                    if(!_permissionsRepository.postNotificationsGranted)
                        return@collect

                    _lastNotificationId = Random.nextInt()

                    val builder = NotificationCompat.Builder(
                        _applicationContext,
                        NOTIFICATION_CHANEL_ID
                    )
                    .setSmallIcon(R.drawable.videocam)
                    .setContentTitle(sourceId)
                    .setContentText(
                        _applicationContext.getString(
                            R.string.motion_detected_notification_text))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(true)
                    .apply {
                        val intent = Intent(
                            _applicationContext,
                            MainActivity::class.java
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            _applicationContext,
                            0,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        setContentIntent(pendingIntent)
                    }
                    .apply {
                        if(_permissionsRepository.fullScreenIntentAllowed) {
                            val pendingIntent = PendingIntent.getActivity(
                                _applicationContext,
                                0,
                                MotionViewActivity.startIntent(_applicationContext, _lastNotificationId),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            @SuppressLint("FullScreenIntentPolicy")
                            setFullScreenIntent(pendingIntent, true)
                        }
                    }

                    @SuppressLint("MissingPermission")
                    _notificationManager.notify(_lastNotificationId, builder.build())
                }
            }
    }

    fun cancelNotification(notificationId: Int) {
        _notificationManager.cancel(notificationId)
    }
}
