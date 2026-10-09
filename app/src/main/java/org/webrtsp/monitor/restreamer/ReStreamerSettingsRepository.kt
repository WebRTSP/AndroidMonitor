package org.webrtsp.monitor.restreamer

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import org.webrtsp.monitor.ApplicationIoScope
import org.webrtsp.monitor.BuildConfig
import org.webrtsp.monitor.ReStreamerSettingsDataStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds


data class ReStreamerSettings(
    val reStreamerEnabled: Boolean,
    val clientId: String,
    val credentials: Credentials?,
    val signalingServerUrl: Uri = BuildConfig.SIGNALING_SERVER_URL.toUri(),
    val viewServerUrl: Uri = BuildConfig.VIEW_SERVER_URL.toUri(),
)

enum class CredentialsState {
    Missing,
    Fetching,
    RetryScheduled,
    Available,
}

private const val MIN_RETRY_INTERVAL = 1
private const val MAX_RETRY_INTERVAL = 16

@Singleton
class ReStreamerSettingsRepository @Inject constructor(
    @param:ReStreamerSettingsDataStore private val _reStreamerDataStore: DataStore<Preferences>,
    private val _credentialsDataSource: CredentialsDataSource,
    @ApplicationIoScope applicationScope: CoroutineScope,
) {
    companion object {
        const val TAG = "ReStreamerSettingsRepository"
    }

    private object Keys {
        val RE_STREAMER_ENABLED = booleanPreferencesKey("re_streamer_enabled")
        val WEBRTSP_CLIENT_ID = stringPreferencesKey("webrtsp_client_id")
        val WEBRTSP_AGENT_ID = stringPreferencesKey("webrtsp_agent_id")
        val WEBRTSP_ACCESS_TOKEN = stringPreferencesKey("webrtsp_access_token")
    }
    private object Defaults {
        const val RE_STREAMER_ENABLED = false
    }

    val settingsFlow: Flow<ReStreamerSettings> = _reStreamerDataStore.data
        .map { preferences ->
            val clientId = preferences[Keys.WEBRTSP_CLIENT_ID].let { clientId ->
                clientId ?: "a.${UUID.randomUUID()}".also { newClientId ->
                    _reStreamerDataStore.edit { preferences ->
                        preferences[Keys.WEBRTSP_CLIENT_ID] = newClientId
                    }
                }
            }

            val agentId = preferences[Keys.WEBRTSP_AGENT_ID]
            val accessToken = preferences[Keys.WEBRTSP_ACCESS_TOKEN]
            val credentials = if(agentId != null && accessToken != null)
                Credentials(agentId, accessToken)
            else
                null

            ReStreamerSettings(
                preferences[Keys.RE_STREAMER_ENABLED]
                    ?: Defaults.RE_STREAMER_ENABLED,
                clientId,
                credentials)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val credentialsStateFlow: Flow<CredentialsState> = settingsFlow
        .distinctUntilChanged { oldSettings, settings ->
            oldSettings.clientId == settings.clientId &&
            oldSettings.reStreamerEnabled == settings.reStreamerEnabled &&
            oldSettings.credentials == settings.credentials
        }
        .flatMapLatest { settings ->
            callbackFlow {
                if(settings.credentials != null) {
                    send(CredentialsState.Available)
                } else if(!settings.reStreamerEnabled) {
                    send(CredentialsState.Missing)
                } else {
                    var lastRetryDelay = MIN_RETRY_INTERVAL
                    while(true) {
                        send(CredentialsState.Fetching)

                        val credentials = _credentialsDataSource.registerClient(settings.clientId)
                        if(credentials != null) {
                            send(CredentialsState.Available)
                            setReStreamerCredentials(credentials)
                            break
                        }

                        if(lastRetryDelay < MAX_RETRY_INTERVAL)
                            lastRetryDelay *= 2

                        val retryDelay = lastRetryDelay * 0.8f + lastRetryDelay * 0.4f * Random.nextFloat()
                        Log.i(TAG, "Scheduling retry in $retryDelay seconds...")
                        send(CredentialsState.RetryScheduled)
                        delay((retryDelay * 1000).toLong().milliseconds)
                    }
                }

                awaitClose()
            }
        }
        .shareIn(
            scope = applicationScope,
            started = SharingStarted.WhileSubscribed(5000),
            replay = 1,
        )

    suspend fun setReStreamerEnabled(reStreamerEnabled: Boolean) {
        _reStreamerDataStore.edit { preferences ->
            preferences[Keys.RE_STREAMER_ENABLED] = reStreamerEnabled
        }
    }

    suspend fun setReStreamerCredentials(credentials: Credentials?) {
        _reStreamerDataStore.edit { preferences ->
            if(credentials != null) {
                preferences[Keys.WEBRTSP_AGENT_ID] = credentials.agentId
                preferences[Keys.WEBRTSP_ACCESS_TOKEN] = credentials.accessToken
            } else {
                preferences.remove(Keys.WEBRTSP_AGENT_ID)
                preferences.remove(Keys.WEBRTSP_ACCESS_TOKEN)
            }
        }
    }
}
