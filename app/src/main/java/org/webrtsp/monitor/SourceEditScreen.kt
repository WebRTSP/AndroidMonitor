package org.webrtsp.monitor

import android.content.res.Configuration
import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import org.webrtsp.monitor.ui.theme.spacing

val SUPPORTED_PROTOCOLS = listOf("rtsp://", "http://")

@Composable
fun AddSourceCard(
    modifier: Modifier,
    isVertical: Boolean,
    onAdd: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(MaterialTheme.spacing.cardContentPadding)
                .run {
                    if(isVertical) {
                        sizeIn(maxWidth = MaterialTheme.spacing.sourceCardMaxWidth)
                        .fillMaxWidth()
                        .height(MaterialTheme.spacing.sourceCardPreferredHeight)
                    } else {
                        size(
                            MaterialTheme.spacing.sourceCardPreferredWidth,
                            MaterialTheme.spacing.sourceCardPreferredHeight)
                    }
                },
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.spacing.cardVerticalSpacing,
                Alignment.CenterVertically
            )
        ) {
            FilledIconButton(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally),
                onClick = { onAdd() },
            ) {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
fun SourceCard(
    modifier: Modifier,
    isVertical: Boolean,
    source: Source,
    allowSelect: Boolean,
    onSelect: (source: Source) -> Unit
) {
    val headline: String
    val supporting: String

    if(source.name.isNullOrBlank()) {
        headline = source.url.toOrigin()
        supporting = String()
    } else {
        headline = source.name
        supporting = source.url.toOrigin()
    }

    Card(
        colors = CardDefaults.cardColors(
            MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline),
        modifier = modifier
            .run {
                if(allowSelect) {
                    selectable(
                        selected = false,
                        onClick = { onSelect(source) },
                    )
                } else {
                    this
                }
            },
    ) {
        Column(
            modifier = Modifier
                .padding(MaterialTheme.spacing.cardContentPadding)
                .run {
                    if(isVertical)
                        sizeIn(
                            maxWidth = MaterialTheme.spacing.sourceCardMaxWidth,
                            minHeight = MaterialTheme.spacing.sourceCardPreferredHeight)
                        .fillMaxWidth()
                    else
                        sizeIn(
                            MaterialTheme.spacing.sourceCardPreferredWidth,
                            MaterialTheme.spacing.sourceCardPreferredHeight,
                            MaterialTheme.spacing.sourceCardPreferredWidth)
                },
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.spacing.cardVerticalSpacing,
                Alignment.CenterVertically
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(headline)
                if(source.onvif && !(source.online ?: false)) {
                    Icon(
                        painter = painterResource(R.drawable.videocam_off),
                        contentDescription = null,
                    )
                }
            }
            if(supporting.isNotEmpty()) {
                Text(supporting)
            }
        }
    }
}

@Composable
fun SelectedSourceEditCard(
    modifier: Modifier,
    isVertical: Boolean,
    source: Source,
    nameState: TextFieldState,
    urlState: TextFieldState,
    userNameState: TextFieldState,
    passwordState: TextFieldState,
) {
    val headline: String
    val supporting: String

    if(source.name.isNullOrBlank()) {
        headline = source.url.toOrigin()
        supporting = String()
    } else {
        headline = source.name
        supporting = source.url.toOrigin()
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Card(
        colors = CardDefaults.cardColors(
            MaterialTheme.colorScheme.primaryContainer
        ),
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(MaterialTheme.spacing.cardContentPadding)
                .run {
                    if(isVertical)
                        sizeIn(
                            maxWidth = MaterialTheme.spacing.sourceCardMaxWidth,
                            minHeight = MaterialTheme.spacing.sourceCardPreferredHeight)
                        .fillMaxWidth()
                    else
                        sizeIn(
                            MaterialTheme.spacing.sourceCardPreferredWidth,
                            MaterialTheme.spacing.sourceCardPreferredHeight,
                            MaterialTheme.spacing.sourceCardPreferredWidth)
                },
            verticalArrangement = Arrangement.spacedBy(
                MaterialTheme.spacing.cardVerticalSpacing,
                Alignment.CenterVertically),
        ) {
            if(source.onvif) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(headline)
                    if(!(source.online ?: false)) {
                        Icon(
                            painter = painterResource(R.drawable.videocam_off),
                            contentDescription = null,
                        )
                    }
                }
                if(supporting.isNotEmpty()) {
                    Text(supporting)
                }
                TextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    state = userNameState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    label = {
                        Text(stringResource(R.string.source_user_name_label))
                    },
                )
                SecureTextField(
                    modifier = Modifier.fillMaxWidth(),
                    state = passwordState,
                    label = {
                        Text(stringResource(R.string.source_password_label))
                    },
                )
            } else {
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    state = nameState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    label = {
                        Text(stringResource(R.string.source_name_label))
                    },
                )
                TextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    state = urlState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    label = {
                        Text(stringResource(R.string.source_url_label))
                    },
                    placeholder = {
                        Text(stringResource(R.string.source_url_placeholder))
                    },
                )
            }
        }
    }
}

@Composable
fun SourceEditScreen(
    onComplete: (activeSource: Source?) -> Unit,
    viewModel: SourceEditViewModel = hiltViewModel()
) {
    val configuration = LocalConfiguration.current
    val isLandscape by remember(configuration) {
        derivedStateOf {
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
    }
    val isLoading by viewModel.loading.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.discovering.collectAsStateWithLifecycle()
    val activeSource by viewModel.activeSource.collectAsStateWithLifecycle()
    val selectedSource by viewModel.selectedSource.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()

    val onSelect = remember {{ source: Source ->
        viewModel.selectSource(source)
    }}

    val onAdd = remember {{
        viewModel.createNewSourceAndSelect()
    }}

    val selectedSourceIsValid by remember {
        derivedStateOf {
            selectedSource?.let {
                if(it.origin == SourceOrigin.User) {
                    val url = viewModel.selectedSourceUrlState.text
                    SUPPORTED_PROTOCOLS.any { prefix -> url.startsWith(prefix) } &&
                    Patterns.WEB_URL.matcher(url).matches()
                } else {
                    true
                }
            } ?: false
        }
    }

    val allowChangeSelected by remember {
        derivedStateOf { selectedSource == null || selectedSourceIsValid }
    }

    val enableConfirmButton = selectedSourceIsValid

    BackHandler {
        onComplete(activeSource)
    }

    val items: LazyListScope.(isVertical: Boolean) -> Unit = { isVertical ->
        val selectedSource = selectedSource

        items(sources, key = { if(it.onvif) it.url.toOrigin() else it.id!! }) { source ->
            val activateEdit = selectedSource != null && selectedSource.isTheSameAs(source)
            AnimatedContent(
                modifier = Modifier.animateItem(),
                targetState = activateEdit,
                transitionSpec = { fadeIn().togetherWith(fadeOut()) }
            ) { activateEdit ->
                if(activateEdit) {
                    SelectedSourceEditCard(
                        Modifier,
                        isVertical,
                        source,
                        viewModel.selectedSourceNameState,
                        viewModel.selectedSourceUrlState,
                        viewModel.selectedSourceUserNameState,
                        viewModel.selectedSourcePasswordState)
                } else {
                    SourceCard(
                        Modifier,
                        isVertical,
                        source,
                        allowChangeSelected,
                        onSelect)
                }
            }
        }
        if(selectedSource != null && selectedSource.id == null && !selectedSource.onvif ) {
            item(key = "edit source") {
                SelectedSourceEditCard(
                    Modifier.animateItem(),
                    isVertical,
                    selectedSource,
                    viewModel.selectedSourceNameState,
                    viewModel.selectedSourceUrlState,
                    viewModel.selectedSourceUserNameState,
                    viewModel.selectedSourcePasswordState)
            }
        }
        if(selectedSource == null || enableConfirmButton) {
            // add allowed only if there is no active new item, or if it valid
            item(key = "add new source") {
                AddSourceCard(
                    Modifier.animateItem(),
                    isVertical,
                    onAdd
                )
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.rowSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.sources_screen_title),
                        )
                        AnimatedVisibility(
                            visible = isDiscovering,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally(),
                        ) {
                            WaitingIcon()
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onComplete(activeSource) },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    AnimatedVisibility(
                        visible = selectedSource != null &&
                            (selectedSource.userDefined ||
                                (selectedSource.onvif && !selectedSource.online)),
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.dropSelected()
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = null,
                            )
                        }
                    }
                    FilledIconButton(
                        onClick = {
                            viewModel.updateSelected(activate = true)
                            onComplete(viewModel.selectedSource.value)
                        },
                        enabled = enableConfirmButton,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.check),
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
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .padding(MaterialTheme.spacing.screenPadding)
                .fillMaxSize()
        ) {
            AnimatedVisibility(
                visible = !isLoading,
                modifier = Modifier
                    .align(Alignment.Center),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                if(
                    isLandscape ||
                    currentWindowAdaptiveInfo()
                        .windowSizeClass
                            .isWidthAtLeastBreakpoint(
                                WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(
                            MaterialTheme.spacing.cardVerticalSpacing),
                        verticalAlignment = Alignment.Top,
                    ) {
                        items(false)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(
                            MaterialTheme.spacing.cardVerticalSpacing),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        items(true)
                    }
                }
            }
        }
    }
}
