package org.webrtsp.monitor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.webrtsp.monitor.restreamer.CredentialsState
import org.webrtsp.monitor.ui.theme.spacing


@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val delayedUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uiState: SettingsViewModel.UiState
    when(val delayedUiState = delayedUiState) {
        DelayedValue.Loading -> return
        is DelayedValue.Ready -> uiState = delayedUiState.value
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.settings_screen_title))
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onBack() },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(MaterialTheme.spacing.screenPadding)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.spacing.settingsItemsVerticalSpacing
            )
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = MaterialTheme.spacing.settingsContentMaxWidth)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.track_motion_label) + "\n")
                        withStyle(
                            style = SpanStyle(
                                color = LocalContentColor.current.copy(alpha = .6f),
                                fontSize = LocalTextStyle.current.fontSize * .8f,
                            )
                        ) {
                            append(stringResource(R.string.track_motion_label_supporting))
                        }
                    }
                )
                Switch(
                    checked = uiState.trackMotion,
                    onCheckedChange = { viewModel.setTrackMotion(it) }
                )
            }
            Row(
                modifier = Modifier
                    .widthIn(max = MaterialTheme.spacing.settingsContentMaxWidth)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.keep_screen_on_label))
                Switch(
                    checked = uiState.keepScreenOn,
                    onCheckedChange = { viewModel.setKeepScreenOn(it) }
                )
            }
            Row(
                modifier = Modifier
                    .widthIn(max = MaterialTheme.spacing.settingsContentMaxWidth)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.restreamer_enabled_label) + "\n")
                        withStyle(
                            style = SpanStyle(
                                color = LocalContentColor.current.copy(alpha = .6f),
                                fontSize = LocalTextStyle.current.fontSize * .8f,
                            )
                        ) {
                            append(stringResource(R.string.restreamer_enabled_label_supporting))
                        }
                    }
                )
                Switch(
                    checked = uiState.reStreamerEnabled,
                    onCheckedChange = { viewModel.setReStreamerEnabled(it) }
                )
            }

            AnimatedContent(
                targetState = if(uiState.reStreamerEnabled)
                    when(uiState.credentialsState) {
                        CredentialsState.RetryScheduled,
                        CredentialsState.Fetching -> CredentialsState.Fetching
                        CredentialsState.Available,
                        CredentialsState.Missing -> uiState.credentialsState
                    }
                else
                    CredentialsState.Missing,
                modifier = Modifier
                    .widthIn(max = MaterialTheme.spacing.settingsContentMaxWidth)
                    .fillMaxWidth(),
                transitionSpec = {
                    (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
                },
            ) { credentialsState ->
                when(credentialsState) {
                    CredentialsState.Fetching,
                    CredentialsState.RetryScheduled -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                MaterialTheme.spacing.rowSpacing,
                                Alignment.CenterHorizontally),
                        ) {
                            Text(
                                stringResource(R.string.registering_device_label),
                                style = TextStyle(
                                    color = LocalContentColor.current.copy(alpha = .6f),
                                    fontSize = LocalTextStyle.current.fontSize * .8f,
                                ),
                            )
                            WaitingIcon()
                        }
                    }
                    CredentialsState.Available -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                MaterialTheme.spacing.rowSpacing,
                                Alignment.CenterHorizontally),
                        ) {
                            Text(
                                stringResource(R.string.device_id_label),
                                style = TextStyle(
                                    color = LocalContentColor.current.copy(alpha = .6f),
                                    fontSize = LocalTextStyle.current.fontSize * .8f,
                                ),
                            )
                            SelectionContainer() {
                                Text(
                                    uiState.agentId,
                                    style = TextStyle(
                                        color = LocalContentColor.current.copy(alpha = .6f),
                                        fontSize = LocalTextStyle.current.fontSize * .8f,
                                    ),
                                )
                            }
                        }
                    }
                    CredentialsState.Missing -> {}
                }
            }
        }
    }
}
