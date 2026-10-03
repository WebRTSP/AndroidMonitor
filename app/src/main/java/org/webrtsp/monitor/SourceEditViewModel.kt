package org.webrtsp.monitor

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.webrtsp.monitor.onvif.ONVIFDiscoverer
import org.webrtsp.monitor.restreamer.ReStreamerSettingsRepository
import org.webrtsp.monitor.restreamer.toReStreamSource
import javax.inject.Inject


@HiltViewModel
class SourceEditViewModel @Inject constructor(
    private val _settingsRepository: SettingsRepository,
    private val _reStreamerSettingsRepository: ReStreamerSettingsRepository,
) : ViewModel() {
    companion object {
        const val TAG = "SourceEditViewModel"
    }

    private var _discoverer = ONVIFDiscoverer()
    private val _discoveredCams = _discoverer.discovered
    val discovering = _discoverer.state
        .map { state ->
            when (state) {
                ONVIFDiscoverer.State.Preparing,
                ONVIFDiscoverer.State.Scanning -> true
                ONVIFDiscoverer.State.Done,
                ONVIFDiscoverer.State.Error -> false
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val _activeSourceId = _settingsRepository.activeSourceIdFlow
        .map { DelayedValue.Ready(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = DelayedValue.Loading
        )
    val activeSource = _settingsRepository.activeSourceFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    val loading = _settingsRepository.activeSourceFlow
        .map { false }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )
    val sharingEnabled = _reStreamerSettingsRepository.settingsFlow
        .map { settings ->
            settings.reStreamerEnabled && settings.credentials != null
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val sources = combine(
            _settingsRepository.allSourcesFlow,
            _discoveredCams
        ) { saved, discovered ->
            val discovered = discovered.associateByTo(
                mutableMapOf<UrlOrigin, ONVIFDiscoverer.Camera>()
            ) { it.endpoint.toOrigin() }

            val sources = saved.map {
                var source = it.toSource()
                discovered.remove(source.url.toOrigin())
                    ?.also { discoveredSource ->
                        source = source.copy(
                            name = discoveredSource.name,
                            online = true)
                    }
                source
            } + discovered.map { (_, camera) -> camera.toSource() }

            sources.toPersistentList()
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf<Source>()
        )

    private val _selectedSource = MutableStateFlow<Source?>(null)
    val selectedSource = _selectedSource.asStateFlow()
    val selectedSourceNameState = TextFieldState()
    val selectedSourceUrlState = TextFieldState()
    val selectedSourceUserNameState = TextFieldState()
    val selectedSourcePasswordState = TextFieldState()
    init {
        viewModelScope.launch {
            val activeSource = _settingsRepository.activeSourceFlow.first()
            _selectedSource.value = activeSource
            activeSource?.also { activeSource ->
                selectedSourceNameState.setTextAndPlaceCursorAtEnd(activeSource.name ?: String())
                selectedSourceUrlState.setTextAndPlaceCursorAtEnd(activeSource.url.toString())
                selectedSourceUserNameState.setTextAndPlaceCursorAtEnd(activeSource.userName ?: String())
                selectedSourcePasswordState.setTextAndPlaceCursorAtEnd(activeSource.password ?: String())
            }
        }
    }

    fun createNewSourceAndSelect() {
        selectSource(
            Source(
                id = null,
                Uri.EMPTY,
                SourceOrigin.User,
                userName = null,
                password = null,
                name = null,
                urn = null,
                online = null,
            )
        )
    }

    fun selectSource(source: Source) {
        updateSelected()

        _selectedSource.value = source
        selectedSourceNameState.setTextAndPlaceCursorAtEnd(source.name ?: String())
        selectedSourceUrlState.setTextAndPlaceCursorAtEnd(source.url.toString())
        selectedSourceUserNameState.setTextAndPlaceCursorAtEnd(source.userName ?: String())
        selectedSourcePasswordState.setTextAndPlaceCursorAtEnd(source.password ?: String())
    }

    fun updateSelected(activate: Boolean = false): Job? {
        var selectedSource = selectedSource.value ?: return null

        val userName = selectedSourceUserNameState.text.toString().run { ifEmpty { null } }
        val password = selectedSourcePasswordState.text.toString().run { ifEmpty { null } }
        selectedSource = if(selectedSource.origin == SourceOrigin.User) {
            val name = selectedSourceNameState.text.toString().run { ifEmpty { null } }
            val url = selectedSourceUrlState.text.toString()
            selectedSource.copy(
                name = name,
                url = url.toUri(),
                userName = userName,
                password = password)
        } else {
            selectedSource.copy(
                userName = userName,
                password = password)
        }.also {
            _selectedSource.value = it
        }

        return viewModelScope.launch {
            val databaseId = _settingsRepository.addOrUpdate(selectedSource)
            if(activate) {
                _settingsRepository.setActiveSource(databaseId)
            }
        }
    }

    fun dropSelected(): Job? {
        val selectedSource = selectedSource.value ?: return null
        val activeSource = activeSource.value

        if(activeSource != null && activeSource.isTheSameAs(selectedSource)) {
            _selectedSource.value = null
        } else {
            _selectedSource.value = activeSource
        }

        return viewModelScope.launch {
            (_activeSourceId.first { it != DelayedValue.Loading } as DelayedValue.Ready)
                .also { sourceId ->
                    if(sourceId.value == selectedSource.id)
                        _settingsRepository.setActiveSource(null)
                }

            _settingsRepository.drop(selectedSource)
        }
    }

    suspend fun buildSourcesShareText(): String {
        return combine(_settingsRepository.allSourcesFlow,
            _reStreamerSettingsRepository.settingsFlow
        ) { sources, settings ->
            val credentials = settings.credentials ?: return@combine String()

            updateSelected()?.join()

            sources.joinToString(separator = "\n") { source ->
                with(source.toReStreamSource()) {
                    "${if(name.isNullOrBlank()) url.toUri().toOrigin() else name}: " +
                    "${settings.viewServerUrl}#${accessToken}@${credentials.agentId}/${id}"
                }
            }
        }.first()
    }

    override fun onCleared() {
        _discoverer.close()
    }
}
