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
import android.net.Uri
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

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
class SourcesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val sourcesDao: SourcesDao,
) {
    private object Keys {
        val URL = stringPreferencesKey("url")
    }

    val activeSourceUrlFlow: Flow<Uri?> = dataStore.data.map { preferences ->
        val urlString = preferences[Keys.URL]

        if(urlString.isNullOrBlank()) {
            null
        } else {
            urlString.toUri()
        }
    }

    suspend fun updateUrl(url: Uri) {
        dataStore.edit { preferences ->
            preferences[Keys.URL] = url.toString()
        }
    }

    val allSources = sourcesDao.all()
}
