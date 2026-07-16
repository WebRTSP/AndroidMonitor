package org.webrtsp.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerStateRepository @Inject constructor(
    @param:ApplicationContext val _applicationContext: Context,
    @ApplicationScope applicationScope: CoroutineScope,
) {
    private val _isOnPowerNow: Boolean
        get() {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = _applicationContext.registerReceiver(null, intentFilter)
            val batteryStatus = intent?.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN)

            return batteryStatus != null && (
                batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                batteryStatus == BatteryManager.BATTERY_STATUS_FULL
            )
        }

    val isOnPower: StateFlow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_POWER_CONNECTED -> trySend(true)
                    Intent.ACTION_POWER_DISCONNECTED -> trySend(false)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        ContextCompat.registerReceiver(
            _applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        awaitClose {
            _applicationContext.unregisterReceiver(receiver)
        }
    }
    .stateIn(
        scope = applicationScope,
        started = SharingStarted.WhileSubscribed(5000), // Держим подписку активной еще 5 сек после ухода с экрана
        initialValue = _isOnPowerNow,
    )
}
