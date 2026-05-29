package org.webrtsp.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.net.Uri
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import javax.inject.Inject


@HiltViewModel
class SourceEditViewModel @Inject constructor(
    private val sourcesRepository: SourcesRepository
) : ViewModel() {
    private var _discoverer = ONVIFDiscoverer()
    private val _discoveredCams = _discoverer.discovered
    val discoveryState = _discoverer.state

    val savedSources = sourcesRepository.allSources

    val activeSourceUrl = sourcesRepository.activeSourceFlow
        .map { url -> DelayedValue.Ready(url) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = DelayedValue.Loading
        )

    val sources = savedSources.combine(_discoveredCams) { saved, discovered ->
            val discovered = discovered.associateByTo(
                mutableMapOf<SourceId, ONVIFDiscoverer.Camera>()
            ) {
                it.endpoint.toSourceId()
            }

            val sources = saved.associateTo(
                mutableMapOf<SourceId, Source>()
            ) { sourceEntity ->
                val source = sourceEntity.toSource()
                discovered.remove(source.id)
                source.id to source
            }

            discovered.forEach { (id, camera) ->
                sources[id] = camera.toSource()
            }

            sources.values.toPersistentList()
        } .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf<Source>()
        )

    fun updateSourceUrl(url: Uri) {
        viewModelScope.launch {
            sourcesRepository.updateUrl(url)
        }
    }

    override fun onCleared() {
        super.onCleared()

        _discoverer.close()
    }
}
