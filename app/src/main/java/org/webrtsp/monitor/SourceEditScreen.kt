package org.webrtsp.monitor

import android.content.res.Configuration
import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.webrtsp.monitor.ui.theme.MonitorTheme
import org.webrtsp.monitor.ui.theme.buttonLabel
import org.webrtsp.monitor.ui.theme.spacing
import android.net.Uri
import java.net.URISyntaxException
import androidx.core.net.toUri

@Composable
fun SourceEdit(url: Uri?, updateUrlAndBack: (url: Uri) -> Unit) {
    val isNew = url == null

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            MaterialTheme.spacing.verticalSpacing,
            Alignment.CenterVertically
        )
    ) {
        var urlString by rememberSaveable { mutableStateOf(url?.toString() ?: String()) }
        val parsedUrl by remember {
            derivedStateOf {
                if (!Patterns.WEB_URL.matcher(urlString).matches()) {
                    null
                } else {
                    urlString.toUri()
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

@Composable
fun Source(
    source: Source,
    isSelected: Boolean = true,
    onSelect: (uri: String) -> Unit
) {
    val headline: String
    val supporting: String

    if(source.name.isNullOrBlank()) {
        headline = source.id
        supporting = String()
    } else {
        headline = source.name
        supporting = source.id
    }

    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting) },
        modifier = Modifier.selectable(
            selected = isSelected,
            onClick = { onSelect(source.id) },
        ),
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            headlineColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    )
}

@Composable
fun SourceEditScreen(
    backStack: NavBackStack<NavKey>,
    viewModel: SourceEditViewModel = hiltViewModel()
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sourceUrl by viewModel.activeSourceUrl.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()

    if(sourceUrl !is DelayedValue.Ready) {
        return
    }

    var selectedCam by rememberSaveable {
        mutableStateOf(
            (sourceUrl as DelayedValue.Ready<Source?>).value?.endpoint?.toSourceId()
        )
    }
    val onSelect = remember {{ uri: String -> selectedCam = uri }}

    val items:  LazyListScope.() -> Unit = {
        /*
        when(val sourceUrl = sourceUrl) {
            DelayedValue.Loading -> {}
            is DelayedValue.Ready -> {
                item {
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
        */

        items(sources, key = { it.id }) { source ->
            Source(source, selectedCam == source.id, onSelect)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        if(isLandscape) {
            LazyRow(modifier = Modifier
                .padding(innerPadding)
                .padding(MaterialTheme.spacing.screenPadding)
            ) {
                items()
            }
        } else {
            LazyColumn(modifier = Modifier
                .padding(innerPadding)
                .padding(MaterialTheme.spacing.screenPadding)
            ) {
                items()
            }
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
        SourceEdit(url = "rtsp://127.0.0.1:554".toUri(), {})
    }
}
