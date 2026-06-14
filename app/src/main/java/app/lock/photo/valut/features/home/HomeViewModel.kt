package app.lock.photo.valut.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.lock.AppLockStateManager
import app.lock.photo.valut.data.local.dao.IntruderAttemptDao
import app.lock.photo.valut.data.local.dao.LockedAppDao
import app.lock.photo.valut.data.local.dao.VaultAlbumDao
import app.lock.photo.valut.data.local.dao.VaultMediaDao
import app.lock.photo.valut.domain.model.UnlockMethod
import app.lock.photo.valut.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the home dashboard. Counts are sourced reactively from Room — real vault
 * figures, no placeholders.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    vaultMediaDao: VaultMediaDao,
    vaultAlbumDao: VaultAlbumDao,
    lockedAppDao: LockedAppDao,
    intruderAttemptDao: IntruderAttemptDao,
    private val settingsRepository: SettingsRepository,
    private val appLockStateManager: AppLockStateManager
) : ViewModel() {

    private val lockNowEvents = Channel<UnlockMethod>(Channel.BUFFERED)
    val lockNowFlow = lockNowEvents.receiveAsFlow()

    fun lockNow() {
        viewModelScope.launch {
            appLockStateManager.markLocked()
            lockNowEvents.trySend(settingsRepository.unlockMethod.first())
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        vaultMediaDao.observeVaultCounts(),
        vaultAlbumDao.observeAlbumCount(),
        lockedAppDao.observeLockedCount(),
        intruderAttemptDao.observeAttemptCount(),
        settingsRepository.appLockEnabled
    ) { counts, albums, lockedApps, intruders, appLockEnabled ->
        HomeUiState(
            lockedApps = lockedApps,
            photos = counts.photoCount,
            videos = counts.videoCount,
            albums = albums,
            favorites = counts.favoriteCount,
            recycleBin = counts.recycleBinCount,
            intruderAlerts = intruders,
            storageUsedBytes = counts.storageUsedBytes,
            appLockEnabled = appLockEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )
}
