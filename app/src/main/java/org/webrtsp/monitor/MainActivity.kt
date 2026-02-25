package org.webrtsp.monitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import org.webrtsp.monitor.ui.theme.MonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MonitorTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                ) { innerPadding ->
                    Box(modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                    ) {
                        Icon(
                            modifier = Modifier
                                .fillMaxWidth(0.3f)
                                .aspectRatio(1f)
                                .align(Alignment.Center),
                            painter = painterResource(R.drawable.videocam_off),
                            contentDescription = null,
                            tint = colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MonitorTheme {
    }
}
