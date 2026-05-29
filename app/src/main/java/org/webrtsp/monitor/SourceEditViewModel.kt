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
import javax.inject.Inject


@HiltViewModel
class SourceEditViewModel @Inject constructor(
    private val sourcesRepository: SourcesRepository
) : ViewModel() {
    private var _discoverer = ONVIFDiscoverer()

    val discoveredCams = _discoverer.discovered
    val discoveryState = _discoverer.state

    val savedSources = sourcesRepository.allSources

    val activeSourceUrl = sourcesRepository.activeSourceUrlFlow
        .map { url -> DelayedValue.Ready(url) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = DelayedValue.Loading
        )

    val sources = savedSources.combine(discoveredCams) { saved, discovered ->
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

            sources.toPersistentMap()
        } .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = mutableMapOf<SourceId, Source>()
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
