package org.webrtsp.monitor

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.webrtsp.monitor.ui.theme.MonitorTheme
import org.webrtsp.monitor.ui.theme.buttonLabel
import org.webrtsp.monitor.ui.theme.spacing
import java.net.URI
import java.net.URISyntaxException

@Composable
fun SourceEdit(url: URI?, updateUrlAndBack: (url: URI) -> Unit) {
    val isNew = url == null

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(MaterialTheme.spacing.screenPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.spacing.verticalSpacing,
                Alignment.CenterVertically
            )
        ) {
            var urlString by rememberSaveable { mutableStateOf(url?.toString() ?: "") }
            val parsedUrl by remember {
                derivedStateOf {
                    if (!Patterns.WEB_URL.matcher(urlString).matches()) {
                        null
                    } else try {
                        URI(urlString)
                    } catch (_: URISyntaxException) {
                        null
                    }
                }
            }

            OutlinedTextField(
                value = urlString,
                onValueChange = { value ->
                    urlString = value
                },
                placeholder = {
                    Text(stringResource(R.string.url_placeholder))
                },
                singleLine = true,
            )

            val enableButton by remember {
                derivedStateOf {
                    parsedUrl != null && parsedUrl != url
                }
            }

            val buttonTextResource = if (isNew)
                R.string.add_button_text
            else
                R.string.update_button_text
            Button(
                onClick = { updateUrlAndBack(parsedUrl!!) },
                enabled = enableButton,
            ) {
                Text(
                    stringResource(buttonTextResource),
                    style = MaterialTheme.typography.buttonLabel
                )
            }
        }
    }
}

@Composable
fun SourceEditScreen(
    backStack: NavBackStack<NavKey>,
    viewModel: SourceEditViewModel = hiltViewModel()
) {
    val sourceUrl by viewModel.sourceUrl.collectAsStateWithLifecycle()

    when(val sourceUrl = sourceUrl) {
        DelayedValue.Loading -> {}
        is DelayedValue.Ready -> {
            SourceEdit(
                url = sourceUrl.value,
                updateUrlAndBack = {
                    url -> viewModel.updateSourceUrl(url)
                    backStack.removeLastOrNull()
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NewSourceScreenPreview() {
    MonitorTheme {
        SourceEdit(null, {})
    }
}

@Preview(showBackground = true)
@Composable
fun EditSourceScreenPreview() {
    MonitorTheme {
        SourceEdit(url = URI("rtsp://127.0.0.1:554"), {})
    }
}
