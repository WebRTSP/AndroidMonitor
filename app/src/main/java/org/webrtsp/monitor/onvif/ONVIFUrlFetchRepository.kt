package org.webrtsp.monitor.onvif

import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.webrtsp.monitor.DefaultDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ONVIFUrlFetchRepository @Inject constructor(
    @param:DefaultDispatcher private val _dispatcher: CoroutineDispatcher
) {
    private val _cacheGuard = Mutex()
    private var _deviceUrl: Uri? = null
    private var _mediaUrl: Uri? = null

    suspend fun fetchMediaUrl(
        deviceUrl: Uri,
        userName: String?,
        password: String?,
        ignoreCached: Boolean = false,
    ): Uri? = withContext(_dispatcher) {
        if(!ignoreCached) {
            _cacheGuard.withLock {
                if(_deviceUrl == deviceUrl && _mediaUrl != null) {
                    return@withContext _mediaUrl
                }
            }
        }

        val mediaUrl = ONVIFUrlFetcher(deviceUrl, userName, password).use { fetcher ->
            val finalState = fetcher.state.first { state ->
                state == ONVIFUrlFetcher.State.Done || state == ONVIFUrlFetcher.State.Error
            }

            if(finalState == ONVIFUrlFetcher.State.Done)
                fetcher.fetched.value.firstOrNull()
            else
                null
        }

        if(mediaUrl != null) {
            _cacheGuard.withLock {
                _deviceUrl = deviceUrl
                _mediaUrl = mediaUrl
            }
        }

        mediaUrl
    }

    suspend fun resetCachedMediaUrl(deviceUrl: Uri) {
        _cacheGuard.withLock {
            if(_deviceUrl == deviceUrl) {
                _mediaUrl = null
            }
        }
    }
}
