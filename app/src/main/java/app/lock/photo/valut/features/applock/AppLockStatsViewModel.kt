package app.lock.photo.valut.features.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.data.local.dao.AppLockStatsDao
import app.lock.photo.valut.domain.repository.AppLockRepository
import app.lock.photo.valut.features.applock.model.AppLockStatsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AppLockStatsViewModel @Inject constructor(
    statsDao: AppLockStatsDao,
    repository: AppLockRepository
) : ViewModel() {

    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    val uiState: StateFlow<AppLockStatsUiState> = combine(
        statsDao.observeForDate(today),
        repository.observeLockedCount()
    ) { stats, lockedCount ->
        AppLockStatsUiState(
            lockedAppsCount = lockedCount,
            successfulUnlocks = stats?.successfulUnlocks ?: 0,
            failedUnlocks = stats?.failedUnlocks ?: 0,
            lockedAppOpens = stats?.lockedAppOpenCount ?: 0,
            protectionActiveMillis = stats?.protectionActiveMillis ?: 0L
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockStatsUiState())
}
