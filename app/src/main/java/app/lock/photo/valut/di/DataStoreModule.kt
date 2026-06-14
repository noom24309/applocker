package app.lock.photo.valut.di

import android.content.Context
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideAppSettingsDataStore(
        @ApplicationContext context: Context
    ): AppSettingsDataStore = AppSettingsDataStore(context)
}
