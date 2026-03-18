package org.webrtsp.monitor

import android.view.Surface
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
class MainActivityViewModel @Inject constructor(
    sourceRepository: SourceRepository
) : ViewModel() {
    val hasSource = sourceRepository.urlFlow
        .map { url -> DelayedValue.Ready(url != null) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = DelayedValue.Loading
        )
}
