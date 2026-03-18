package org.webrtsp.monitor

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.webrtsp.monitor.ui.theme.spacing

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
fun WaitingIcon(modifier: Modifier)
{
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
    )
    Icon(
        modifier = modifier
            .graphicsLayer { rotationZ = angle },
        painter = painterResource(R.drawable.progress_activity),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
fun SourceViewScreen(
    editSource: () -> Unit,
    viewModel: SourceViewViewModel = hiltViewModel(),
) {
    val activity = LocalActivity.current

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier
                    .padding(MaterialTheme.spacing.floatingButtonPadding),
                onClick = editSource
            ) {
                Icon(
                    painter = painterResource(R.drawable.edit),
                    contentDescription = null,
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .padding(MaterialTheme.spacing.sourceViewScreenPadding)
                .fillMaxSize()
        ) {
            val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
            val showVideoView by remember {
                derivedStateOf { playbackState == PlaybackState.Playing }
            }
            val showFailedIcon by remember {
                derivedStateOf {
                    playbackState == PlaybackState.Eos ||
                    playbackState == PlaybackState.Error
                }
            }
            val showWaitingIcon by remember {
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

            if(showFailedIcon) {
                Icon(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .aspectRatio(1f)
                        .align(Alignment.Center),
                    painter = painterResource(R.drawable.videocam_alert),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            if(showWaitingIcon) {
                WaitingIcon(modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .aspectRatio(1f)
                    .align(Alignment.Center)
                )
            }
        }
    }
}
