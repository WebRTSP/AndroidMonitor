package org.webrtsp.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerStateRepository @Inject constructor(
    @param:ApplicationContext val _applicationContext: Context,
    @ApplicationScope applicationScope: CoroutineScope,
) {
    private var _plugged = false
    private var _batteryLow = false

    private val _isPowerSufficient: Boolean
        get() = _plugged && !_batteryLow

    val isPowerSufficient: StateFlow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when(intent?.action) {
                    Intent.ACTION_POWER_CONNECTED -> _plugged = true
                    Intent.ACTION_POWER_DISCONNECTED -> _plugged = false
                    Intent.ACTION_BATTERY_LOW -> _batteryLow = true
                    Intent.ACTION_BATTERY_OKAY -> _batteryLow = false
                }

                trySend(_isPowerSufficient)
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }

        withContext(Dispatchers.Main) {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = _applicationContext.registerReceiver(null, intentFilter)
            _plugged = (intent?.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                0) ?: 0) > 0
            _batteryLow = (intent?.getBooleanExtra(
                BatteryManager.EXTRA_BATTERY_LOW,
                false) ?: false)

            trySend(_isPowerSufficient)

            ContextCompat.registerReceiver(
                _applicationContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        }

        awaitClose {
            _applicationContext.unregisterReceiver(receiver)
        }
    }
    .stateIn(
        scope = applicationScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
    )
}
