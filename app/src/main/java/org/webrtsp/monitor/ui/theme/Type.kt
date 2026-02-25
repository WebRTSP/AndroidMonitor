package org.webrtsp.monitor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

val Typography = Typography(
)

val Typography.buttonLabel: TextStyle by lazy {
    Typography.labelLarge.copy(
        fontSize = (Typography.labelLarge.fontSize.value + 2).sp)
}
