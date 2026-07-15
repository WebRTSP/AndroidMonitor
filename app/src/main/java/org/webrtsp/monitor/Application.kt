package org.webrtsp.monitor

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.webrtsp.monitor.onvif.ONVIFEventTrackerService
import org.webrtsp.monitor.restreamer.ReStreamerService
import javax.inject.Inject


@HiltAndroidApp
class Application : android.app.Application() {
    @Inject
    lateinit var permissionsRepository: PermissionsRepository
    @Inject
    lateinit var powerStateRepository: PowerStateRepository
    @Inject
    lateinit var settingsRepository: SettingsRepository
    @Inject
    lateinit var motionEventHandler: MotionEventHandler

    @RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS)
    override fun onCreate() {
        super.onCreate()

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerActivityLifecycleCallbacks(object: ActivityLifecycleCallbacks{
                override fun onActivityCreated(activity: Activity, p1: Bundle?) {}
                override fun onActivityDestroyed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityResumed(activity: Activity) {
                    permissionsRepository.postNotificationsRequestAllowed =
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            activity,
                            Manifest.permission.POST_NOTIFICATIONS)
                }
                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    savedInstanceState: Bundle
                ) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
            })
        }

        val processLifecycleOwner = ProcessLifecycleOwner.get()
        val scope = processLifecycleOwner.lifecycleScope
        val lifecycle = processLifecycleOwner.lifecycle
        scope.launch {
            settingsRepository.settingsFlow
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .combine(powerStateRepository.isOnPower) { settings, isOnPower ->
                    settings to isOnPower
                }
                .collect { (settings, isOnPower) ->
                    val activeSource = settings.activeSource
                    if(
                        isOnPower &&
                        settings.trackMotion &&
                        activeSource != null &&
                        (activeSource.onvif || activeSource.maybeOnvif)
                    ) {
                        ONVIFEventTrackerService.startTracking(
                            applicationContext,
                            activeSource)
                    } else {
                        ONVIFEventTrackerService.stopTracking(applicationContext)
                    }

                    if(settings.reStreamerEnabled) {
                        ReStreamerService.startReStream(applicationContext)
                    } else {
                        ReStreamerService.stopReStream(applicationContext)
                    }
                }
        }
    }
}
