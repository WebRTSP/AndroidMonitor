package org.webrtsp.monitor

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jakarta.inject.Qualifier
import javax.inject.Singleton


@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsDataStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReStreamerSettingsDataStore

@Module
@InstallIn(SingletonComponent::class)
object SettingsDataStoreModule {
    private val Context.settingsDataStore by preferencesDataStore(name = "main")
    private val Context.reStreamerSettingsDataStore by preferencesDataStore(name = "restreamer")

    @Provides
    @Singleton
    @SettingsDataStore
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.settingsDataStore

    @Provides
    @Singleton
    @ReStreamerSettingsDataStore
    fun provideReStreamerSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.reStreamerSettingsDataStore
}
