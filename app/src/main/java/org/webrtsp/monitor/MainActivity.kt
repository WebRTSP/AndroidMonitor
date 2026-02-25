package org.webrtsp.monitor

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable
import org.webrtsp.monitor.ui.theme.MonitorTheme


@Serializable
sealed interface Screen : NavKey {
    @Serializable data object NoSources : Screen
    @Serializable data object SourceEdit : Screen
    @Serializable data object SourceView : Screen
    @Serializable data object Settings: Screen
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        const val TAG = "MainActivity"
    }

    private val _viewModel: MainActivityViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if(granted)
            _viewModel.maybeRequestFullScreenIntentPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)

        enableEdgeToEdge()
        setContent {
            MonitorTheme() {
                val hasSource by _viewModel.hasSourceFlow.collectAsStateWithLifecycle()

                when(val hasSource = hasSource) {
                    DelayedValue.Loading -> {}
                    is DelayedValue.Ready -> {
                        val backStack = rememberNavBackStack(
                            if(hasSource.value)
                                Screen.SourceView
                            else
                                Screen.NoSources)

                        NavDisplay(
                            backStack = backStack,
                            onBack = { backStack.removeLastOrNull() },
                            entryProvider = entryProvider {
                                entry<Screen.NoSources> {
                                    NoSourceScreen(addSource = {
                                        backStack.add(Screen.SourceEdit)
                                    })
                                }
                                entry<Screen.SourceEdit> { key ->
                                    SourceEditScreen(
                                        onComplete = { activeSource ->
                                            backStack.clear()
                                            backStack.add(if(activeSource == null)
                                                Screen.NoSources
                                            else
                                                Screen.SourceView)

                                            if(
                                                (activeSource.onvif || activeSource.maybeOnvif) &&
                                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                            ) {
                                                requestPermissionLauncher.launch(
                                                    Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        },
                                    )
                                }
                                entry<Screen.SourceView> {
                                    val keepScreenOn by _viewModel.keepScreenOn.collectAsStateWithLifecycle()

                                    DisposableEffect(keepScreenOn) {
                                        if(keepScreenOn)
                                            window.addFlags(FLAG_KEEP_SCREEN_ON)
                                        else
                                            window.clearFlags(FLAG_KEEP_SCREEN_ON)

                                        onDispose {
                                            window.clearFlags(FLAG_KEEP_SCREEN_ON)
                                        }
                                    }

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
                                        editSettings = {
                                            backStack.add(Screen.Settings)
                                        },
                                        editSource = {
                                            backStack.add(Screen.SourceEdit)
                                        },
                                        showNavBar = { show ->
                                            if(show)
                                                windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
                                            else
                                                windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
                                        }
                                    )
                                }
                                entry<Screen.Settings> {
                                    SettingsScreen(
                                        onBack = {
                                            backStack.removeLastOrNull()
                                        },
                                    )
                                }
                            },
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
    }
}
