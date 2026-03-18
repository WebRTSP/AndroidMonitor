package org.webrtsp.monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import org.webrtsp.monitor.ui.theme.MonitorTheme
import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen : NavKey {
    @Serializable data object NoSources : Screen
    @Serializable data object SourceEdit : Screen
    @Serializable data object SourceView : Screen
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MonitorTheme() {
                val viewModel: MainActivityViewModel = hiltViewModel()
                val hasSource by viewModel.hasSource.collectAsStateWithLifecycle()
                val backStack = rememberNavBackStack()

                when(val hasSource = hasSource) {
                    DelayedValue.Loading -> return@MonitorTheme
                    is DelayedValue.Ready -> {
                        backStack.add(
                            if(hasSource.value) Screen.SourceView
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
                        entry<Screen.SourceEdit> { key ->
                            SourceEditScreen(backStack)
                        }
                        entry<Screen.SourceView> {
                            SourceViewScreen(editSource = {
                                backStack.add(Screen.SourceEdit)
                            })
                        }
                    }
                )
            }
        }
    }
}
