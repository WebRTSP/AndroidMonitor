package org.webrtsp.monitor

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import org.webrtsp.monitor.ui.theme.spacing


@Composable
fun WaitingBox(modifier: Modifier = Modifier)
{
    BoxWithConstraints(modifier = modifier) {
        val iconSize = min(
            max(
                min(maxHeight, maxWidth) * 0.3f,
                MaterialTheme.spacing.waitingIconMinSize),
           MaterialTheme.spacing.waitingIconMaxSize)
        WaitingIcon(
            modifier = Modifier
                .size(iconSize, iconSize)
                .align(Alignment.Center)
        )
    }
}
