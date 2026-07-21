package org.webrtsp.monitor

import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit


fun Source.toEntity(): SourceEntity {
    return SourceEntity(
        id,
        url.toString(),
        origin,
        userName,
        password,
        name,
        urn)
}

data class Settings(
    val activeSource: Source?,
    val trackMotion: Boolean,
    val keepScreenOn: Boolean,
    val motionPreviewDuration: Duration,
    val fullScreenIntentPermissionRequested: Boolean,
    val reStreamerEnabled: Boolean,
)

data class ReStreamerSettings(
    val serverUrl: Uri,
    val clientId: String,
    val agentId: String?,
    val accessToken: String?,
)

@Singleton
class SettingsRepository @Inject constructor(
    @param:SettingsDataStore private val _dataStore: DataStore<Preferences>,
    private val _sourcesDao: SourcesDao,
    @param:ReStreamerSettingsDataStore private val _reStreamerDataStore: DataStore<Preferences>,
) {
    private object Keys {
        val ACTIVE_SOURCE_ID = longPreferencesKey("active_source_id")
        val TRACK_MOTION = booleanPreferencesKey("track_motion")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val MOTION_PREVIEW_DURATION = intPreferencesKey("motion_preview_duration")
        val FULL_SCREEN_INTENT_PERMISSION_REQUESTED = booleanPreferencesKey("full_screen_intent_permission_requested")
        val RE_STREAMER_ENABLED = booleanPreferencesKey("re_streamer_enabled")
        val WEBRTSP_SERVER_URL = stringPreferencesKey("webrtsp_server_url")
        val WEBRTSP_CLIENT_ID = stringPreferencesKey("webrtsp_client_id")
        val WEBRTSP_AGENT_ID = stringPreferencesKey("webrtsp_agent_id")
        val WEBRTSP_ACCESS_TOKEN = stringPreferencesKey("webrtsp_access_token")
    }
    private object Defaults {
        const val TRACK_MOTION = true
        const val KEEP_SCREEN_ON = false
        val MOTION_PREVIEW_DURATION = 10.seconds
        const val RE_STREAMER_ENABLED = false
        const val WEBRTSP_SERVER_URL = "webrtsps://ipcam.stream/"
    }

    val allSourcesFlow = _sourcesDao.all()

    val activeSourceIdFlow: Flow<SourceId?> = _dataStore.data
        .map { preferences -> preferences[Keys.ACTIVE_SOURCE_ID] }
    val activeSourceFlow: Flow<Source?> = _dataStore.data
        .map { preferences ->
            val activeSourceId = preferences[Keys.ACTIVE_SOURCE_ID]
            if(activeSourceId == null)
                null
            else
                _sourcesDao.findById(activeSourceId)?.toSource() }

    val settingsFlow: Flow<Settings> = _dataStore.data
        .map { preferences ->
            val activeSourceId = preferences[Keys.ACTIVE_SOURCE_ID]
            val activeSource = if(activeSourceId == null)
                null
            else
                _sourcesDao.findById(activeSourceId)?.toSource()

            Settings(
                activeSource,
                preferences[Keys.TRACK_MOTION]
                    ?: Defaults.TRACK_MOTION,
                preferences[Keys.KEEP_SCREEN_ON]
                    ?: Defaults.KEEP_SCREEN_ON,
                preferences[Keys.MOTION_PREVIEW_DURATION]?.seconds
                    ?: Defaults.MOTION_PREVIEW_DURATION,
                preferences[Keys.FULL_SCREEN_INTENT_PERMISSION_REQUESTED]
                    ?: false,
                preferences[Keys.RE_STREAMER_ENABLED]
                    ?: Defaults.RE_STREAMER_ENABLED,
            )
        }

    val reStreamerSettingsFlow: Flow<ReStreamerSettings> = _reStreamerDataStore.data
        .map { preferences ->
            val clientId = preferences[Keys.WEBRTSP_CLIENT_ID].let { clientId ->
                clientId ?: UUID.randomUUID().toString().also { newClientId ->
                    _reStreamerDataStore.edit { preferences ->
                        preferences[Keys.WEBRTSP_CLIENT_ID] = newClientId
                    }
                }
            }

            ReStreamerSettings(
                (preferences[Keys.WEBRTSP_SERVER_URL]
                    ?: Defaults.WEBRTSP_SERVER_URL).toUri(),
                clientId,
                preferences[Keys.WEBRTSP_AGENT_ID],
                preferences[Keys.WEBRTSP_ACCESS_TOKEN])
        }

    val keepScreenOnFlow: Flow<Boolean> = settingsFlow
        .map { settings -> settings.keepScreenOn }
    val motionPreviewDurationFlow: Flow<Duration> = settingsFlow
        .map { settings -> settings.motionPreviewDuration }
    val fullScreenIntentPermissionRequested: Flow<Boolean> = settingsFlow
        .map { settings -> settings.fullScreenIntentPermissionRequested }

    suspend fun setActiveSource(sourceId: SourceId?) {
        _dataStore.edit { preferences ->
            if(sourceId == null)
                preferences.remove(Keys.ACTIVE_SOURCE_ID)
            else
                preferences[Keys.ACTIVE_SOURCE_ID] = sourceId
        }
    }

    suspend fun setTrackMotion(trackMotion: Boolean) {
        _dataStore.edit { preferences ->
            preferences[Keys.TRACK_MOTION] = trackMotion
        }
    }

    suspend fun setKeepScreenOn(keepScreenON: Boolean) {
        _dataStore.edit { preferences ->
            preferences[Keys.KEEP_SCREEN_ON] = keepScreenON
        }
    }

    suspend fun setReStreamerEnabled(reStreamerEnabled: Boolean) {
        _dataStore.edit { preferences ->
            preferences[Keys.RE_STREAMER_ENABLED] = reStreamerEnabled
        }
    }

    suspend fun setMotionPreviewDuration(duration: Duration) {
        _dataStore.edit { preferences ->
            preferences[Keys.MOTION_PREVIEW_DURATION] = duration.toInt(DurationUnit.SECONDS)
        }
    }

    suspend fun setFullScreenIntentPermissionRequested(requested: Boolean) {
        _dataStore.edit { preferences ->
            preferences[Keys.FULL_SCREEN_INTENT_PERMISSION_REQUESTED] = requested
        }
    }

    suspend fun addOrUpdate(source: Source): Long? {
        return try {
            val databaseId = _sourcesDao.upsert(source.toEntity())
            return if(databaseId == SourcesDao.UPDATED) source.id else databaseId
        } catch (_: Exception) {
            null
        }
    }

    suspend fun drop(source: Source) {
        try {
            val sourceId = source.id ?: return
            _sourcesDao.delete(sourceId)
        } catch (_: Exception) {}
    }
}
