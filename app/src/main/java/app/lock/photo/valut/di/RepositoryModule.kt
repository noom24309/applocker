package app.lock.photo.valut.di

import app.lock.photo.valut.data.repository.AppLockRepositoryImpl
import app.lock.photo.valut.data.repository.IntruderRepositoryImpl
import app.lock.photo.valut.data.repository.PremiumToolsRepositoryImpl
import app.lock.photo.valut.data.repository.PrivateDocumentsRepositoryImpl
import app.lock.photo.valut.data.repository.PrivateNotesRepositoryImpl
import app.lock.photo.valut.data.repository.SettingsRepositoryImpl
import app.lock.photo.valut.data.repository.VaultRepositoryImpl
import app.lock.photo.valut.domain.repository.AppLockRepository
import app.lock.photo.valut.domain.repository.IntruderRepository
import app.lock.photo.valut.domain.repository.PremiumToolsRepository
import app.lock.photo.valut.domain.repository.PrivateDocumentsRepository
import app.lock.photo.valut.domain.repository.PrivateNotesRepository
import app.lock.photo.valut.domain.repository.SettingsRepository
import app.lock.photo.valut.domain.repository.VaultRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindVaultRepository(
        impl: VaultRepositoryImpl
    ): VaultRepository

    @Binds
    @Singleton
    abstract fun bindAppLockRepository(
        impl: AppLockRepositoryImpl
    ): AppLockRepository

    @Binds
    @Singleton
    abstract fun bindIntruderRepository(
        impl: IntruderRepositoryImpl
    ): IntruderRepository

    @Binds
    @Singleton
    abstract fun bindPrivateNotesRepository(
        impl: PrivateNotesRepositoryImpl
    ): PrivateNotesRepository

    @Binds
    @Singleton
    abstract fun bindPrivateDocumentsRepository(
        impl: PrivateDocumentsRepositoryImpl
    ): PrivateDocumentsRepository

    @Binds
    @Singleton
    abstract fun bindPremiumToolsRepository(
        impl: PremiumToolsRepositoryImpl
    ): PremiumToolsRepository
}
