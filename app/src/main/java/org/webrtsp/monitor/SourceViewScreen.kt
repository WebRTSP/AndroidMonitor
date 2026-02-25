package org.webrtsp.monitor

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.webrtsp.monitor.ui.theme.spacing
import kotlin.time.Duration.Companion.seconds


@Composable
fun VideoView(
    visible: Boolean,
    surfaceCreated: (surface: Surface) -> Unit,
    surfaceDestroyed: () -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            SurfaceView(context).also { view ->
                view.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        surfaceCreated(holder.surface)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        surfaceDestroyed()
                        // to release all referenced buffers and avoid errors in logcat
                        holder.surface.release()
                    }
                })
            }
        },
        update = { view ->
            // workaround for https://issuetracker.google.com/issues/285718058
            view.visibility = if(visible) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
        },
    )
}

@Composable
fun SourceViewScreen(
    editSettings: (() -> Unit)? = null,
    editSource: (() -> Unit)? = null,
    showNavBar: ((show: Boolean) -> Unit)? = null,
    viewModel: SourceViewViewModel = hiltViewModel(),
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.resumePlayback()
    }

    LifecycleEventEffect(event = Lifecycle.Event.ON_PAUSE) {
        viewModel.detachSurface()
    }

    LifecycleEventEffect(
        lifecycleOwner = ProcessLifecycleOwner.get(),
        event = Lifecycle.Event.ON_STOP,
    ) {
        viewModel.stop()
    }

    var buttonVisible by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(buttonVisible) {
        showNavBar?.invoke(buttonVisible)
    }
    var buttonHideButtonTriggerKey by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(buttonHideButtonTriggerKey) {
        if(buttonVisible) {
            delay(5.seconds)
            buttonVisible = false
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                buttonVisible = !buttonVisible
                if(buttonVisible)
                    ++buttonHideButtonTriggerKey
            },
        floatingActionButton = {
            Column(
                modifier = Modifier
                    .padding(MaterialTheme.spacing.floatingButtonPadding),
                verticalArrangement = Arrangement
                    .spacedBy(MaterialTheme.spacing.floatingButtonPadding)
            ) {
                if(editSettings != null) {
                    AnimatedVisibility(
                        buttonVisible,
                        enter = fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 100)) + scaleIn(),
                        exit = fadeOut(animationSpec = tween(durationMillis = 150)) + scaleOut(),
                    ) {
                        FloatingActionButton(
                            onClick = {
                                editSettings()
                                buttonVisible = false
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.settings),
                                contentDescription = null,
                            )
                        }
                    }
                }

                if(editSource != null) {
                    AnimatedVisibility(
                        buttonVisible,
                        enter = fadeIn(animationSpec = tween(durationMillis = 200)) + scaleIn(),
                        exit = fadeOut(animationSpec = tween(durationMillis = 150)) + scaleOut(),
                    ) {
                        FloatingActionButton(
                            onClick = {
                                editSource()
                                buttonVisible = false
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.edit),
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .padding(MaterialTheme.spacing.sourceViewScreenPadding)
                .fillMaxSize()
        ) {
            val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
            val showVideoView by remember {
                derivedStateOf { playbackState == PlaybackState.Playing }
            }
            val failed by remember {
                derivedStateOf {
                    playbackState == PlaybackState.NoUrl ||
                    playbackState == PlaybackState.Eos ||
                    playbackState == PlaybackState.Error
                }
            }
            val waiting by remember {
                derivedStateOf {
                    playbackState == PlaybackState.Idle || playbackState == PlaybackState.Preparing
                }
            }

            VideoView(
                visible = showVideoView,
                surfaceCreated = { surface ->
                    viewModel.surfaceCreated(surface)
                },
                surfaceDestroyed = {
                    viewModel.surfaceDestroyed()
                }
            )

            AnimatedVisibility(
                visible = failed,
                modifier = Modifier
                    .align(Alignment.Center),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Icon(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .aspectRatio(1f),
                    painter = painterResource(R.drawable.videocam_alert),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            AnimatedVisibility(
                visible = waiting,
                modifier = Modifier
                    .align(Alignment.Center),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                WaitingBox(Modifier.fillMaxSize())
            }
        }
    }
}
