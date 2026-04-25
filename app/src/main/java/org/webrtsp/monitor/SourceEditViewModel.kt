package org.webrtsp.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject

@HiltViewModel
class SourceEditViewModel @Inject constructor(
    private val sourceRepository: SourceRepository
) : ViewModel() {
    private var _discoverer = ONVIFDiscoverer().also { it.discover() }

    val sourceUrl = sourceRepository.urlFlow
        .map { url -> DelayedValue.Ready(url) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = DelayedValue.Loading
        )

    fun updateSourceUrl(url: URI) {
        viewModelScope.launch {
            sourceRepository.updateUrl(url)
        }
    }

    override fun onCleared() {
        super.onCleared()

        _discoverer.close()
    }
}
