package org.webrtsp.monitor

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SourceModule {
    private val Context.sourceDataStore by preferencesDataStore(name = "source")

    @Provides
    @Singleton
    fun provideSourceDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.sourceDataStore
}

@Singleton
class SourceRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val URL = stringPreferencesKey("url")
    }

    val urlFlow: Flow<URI?> = dataStore.data.map { preferences ->
        val urlString = preferences[Keys.URL]

        if(urlString.isNullOrBlank()) {
            null
        } else try {
            URI(urlString)
        } catch (_: URISyntaxException) {
            null
        }
    }

    suspend fun updateUrl(url: URI) {
        dataStore.edit { preferences ->
            preferences[Keys.URL] = url.toString()
        }
    }
}
