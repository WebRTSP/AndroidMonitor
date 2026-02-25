package org.webrtsp.monitor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.webrtsp.monitor.ui.theme.MonitorTheme


@AndroidEntryPoint
class MotionViewActivity : ComponentActivity() {
    companion object {
        const val TAG = "MotionViewActivity"

        private const val NOTIFICATION_ID_EXTRA = "notification id"

        fun startIntent(applicationContext: Context, notificationId: Int): Intent {
            return Intent(
                applicationContext,
                MotionViewActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(NOTIFICATION_ID_EXTRA, notificationId)
            }
        }
    }

    private val _viewModel: MotionViewActivityViewModel by viewModels()

    private fun handleIntent(intent: Intent) {
        _viewModel.notificationId = if(intent.hasExtra(NOTIFICATION_ID_EXTRA))
             intent.getIntExtra(NOTIFICATION_ID_EXTRA, 0)
        else
            null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)

        handleIntent(intent)

        setShowWhenLocked(true)
        setTurnScreenOn(true)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                _viewModel.keepActiveFlow.collect { keepActive ->
                    //if(!keepActive)
                    //    finish()
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            MonitorTheme() {
                val hasSource by _viewModel.hasSourceFlow.collectAsStateWithLifecycle()
                val backStack = rememberNavBackStack()

                BackHandler(enabled = true) {
                    moveTaskToBack(true)
                    finish()
                }

                when (val hasSource = hasSource) {
                    DelayedValue.Loading -> return@MonitorTheme
                    is DelayedValue.Ready -> {
                        backStack.add(
                            if (hasSource.value) Screen.SourceView
                            else Screen.NoSources
                        )
                    }
                }

                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = entryProvider {
                        entry<Screen.NoSources> {
                            NoSourceScreen(addSource = {
                                backStack.add(Screen.SourceEdit)
                            })
                        }
                        entry<Screen.SourceView> {
                            DisposableEffect(Unit) {
                                windowInsetsController.systemBarsBehavior =
                                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                                onDispose {
                                    windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
                                    windowInsetsController.systemBarsBehavior =
                                        WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                                }
                            }

                            SourceViewScreen(
                                showNavBar = { show ->
                                    if(show)
                                        windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
                                    else
                                        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
                                }
                            )
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        Log.d(TAG, "onNewIntent")

        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()

        if(!isChangingConfigurations) {
            finish()
        }
    }
}
