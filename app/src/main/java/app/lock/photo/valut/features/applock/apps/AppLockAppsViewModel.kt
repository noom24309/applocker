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
            .filter { filter == AppFilter.SYSTEM || showSystem || !it.isSystemApp }
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
                    AppFilter.SYSTEM -> it.isSystemApp
                }
            }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * locked-to-total counts for the header, over the user-visible (non-system) apps only,
     * so the number doesn't move when the search box or the filter chips change.
     */
    val protectedSummary: StateFlow<Pair<Int, Int>> = combine(
        installedApps, lockedPackages
    ) { all, locked ->
        val lockedSet = locked.toHashSet()
        val userApps = all.filterNot { it.isSystemApp }
        userApps.count { it.packageName in lockedSet } to userApps.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to 0)

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
            // Locking an app IS the request to be protected: turn protection on right here and
            // arm the self-heal channels, instead of waiting for a separate "activate" step.
            if (locked) serviceManager.activateProtection()
        }
    }

    /** Start the monitor service once it can run — e.g. after permissions were just granted. */
    fun ensureProtectionRunning() {
        viewModelScope.launch { serviceManager.ensureRunning() }
    }

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: AppFilter) { filter.value = value }
    fun toggleSystemApps() { showSystemApps.value = !showSystemApps.value }
}
