package app.lock.photo.valut.features.applock.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.applock.AppLockServiceManager
import app.lock.photo.valut.domain.model.InstalledAppInfo
import app.lock.photo.valut.domain.repository.AppLockRepository
import app.lock.photo.valut.features.applock.model.AppFilter
import app.lock.photo.valut.features.applock.model.InstalledAppUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppLockAppsViewModel @Inject constructor(
    private val repository: AppLockRepository,
    private val serviceManager: AppLockServiceManager
) : ViewModel() {

    private val installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val lockedPackages = repository.observeLockedPackageNames()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(AppFilter.ALL)
    val showSystemApps = MutableStateFlow(false)

    val apps: StateFlow<List<InstalledAppUiModel>> = combine(
        installedApps, lockedPackages, query, filter, showSystemApps
    ) { all, locked, q, filter, showSystem ->
        val lockedSet = locked.toHashSet()
        all.asSequence()
            .filter { showSystem || !it.isSystemApp }
            .filter { q.isBlank() || it.appName.contains(q, ignoreCase = true) }
            .map {
                InstalledAppUiModel(
                    packageName = it.packageName,
                    appName = it.appName,
                    isLocked = it.packageName in lockedSet,
                    isSystemApp = it.isSystemApp
                )
            }
            .filter {
                when (filter) {
                    AppFilter.ALL -> true
                    AppFilter.LOCKED -> it.isLocked
                    AppFilter.UNLOCKED -> !it.isLocked
                }
            }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _loading.value = true
            installedApps.value = repository.loadInstalledApps()
            _loading.value = false
        }
    }

    fun setLocked(app: InstalledAppUiModel, locked: Boolean) {
        viewModelScope.launch {
            repository.setAppLocked(
                InstalledAppInfo(app.packageName, app.appName, app.isSystemApp),
                locked
            )
            // Keep the service in sync: start when first app is locked.
            if (locked && serviceManager.canStartProtection() && !serviceManager.isServiceRunning()) {
                serviceManager.startProtection()
            }
        }
    }

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: AppFilter) { filter.value = value }
    fun toggleSystemApps() { showSystemApps.value = !showSystemApps.value }
}
