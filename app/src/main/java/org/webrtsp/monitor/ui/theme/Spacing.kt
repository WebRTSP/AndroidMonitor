package org.webrtsp.monitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Spacing(
    val screenPadding: Dp = 32.dp,
    val sourceViewScreenPadding: Dp = 0.dp,
    val floatingButtonPadding: Dp = 16.dp,
    val verticalSpacing: Dp = 16.dp,
    val waitingIconMinSize: Dp = 24.dp,
    val waitingIconMaxSize: Dp = 256.dp,
    val sourceCardPreferredWidth: Dp = 256.dp,
    val sourceCardMaxWidth: Dp = 384.dp,
    val sourceCardPreferredHeight: Dp = 64.dp,
    val cardContentPadding: Dp = 16.dp,
    val cardVerticalSpacing: Dp = 16.dp,
    val settingsItemsVerticalSpacing: Dp = 8.dp,
    val discoveringIconHeight: Dp = 32.dp,
    val rowSpacing: Dp = 8.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
