package app.lock.photo.valut.features.applock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.domain.repository.AppLockRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Working state for one app's per-app override settings. */
data class PerAppLockUiState(
    val appName: String = "",
    val useCustom: Boolean = false,
    val unlockMethod: String = "DEFAULT",
    val delayMode: String? = null,
    val fakeMode: String = "USE_GLOBAL",
    val theme: String? = null,
    val hideName: Boolean = false,
    val biometricOnly: Boolean = false,
    val loaded: Boolean = false
)

@HiltViewModel
class PerAppLockSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AppLockRepository
) : ViewModel() {

    private val packageName: String = savedStateHandle[ARG_PACKAGE] ?: ""

    private val _state = MutableStateFlow(PerAppLockUiState())
    val state: StateFlow<PerAppLockUiState> = _state.asStateFlow()

    private val saved = Channel<Unit>(Channel.BUFFERED)
    val savedFlow = saved.receiveAsFlow()

    init {
        viewModelScope.launch {
            val entity = repository.getLockedApp(packageName)
            _state.value = PerAppLockUiState(
                appName = entity?.appName ?: (savedStateHandle[ARG_APP_NAME] ?: packageName),
                useCustom = entity?.useCustomSettings ?: false,
                unlockMethod = entity?.customUnlockMethod ?: "DEFAULT",
                delayMode = entity?.customLockDelayMode,
                fakeMode = entity?.fakeMode ?: "USE_GLOBAL",
                theme = entity?.customLockTheme,
                hideName = entity?.hideAppNameOnLock ?: false,
                biometricOnly = entity?.requireBiometricOnly ?: false,
                loaded = true
            )
        }
    }

    fun setUseCustom(value: Boolean) = _state.update { it.copy(useCustom = value) }
    fun setUnlockMethod(value: String) = _state.update { it.copy(unlockMethod = value) }
    fun setDelayMode(value: String?) = _state.update { it.copy(delayMode = value) }
    fun setFakeMode(value: String) = _state.update { it.copy(fakeMode = value) }
    fun setTheme(value: String?) = _state.update { it.copy(theme = value) }
    fun setHideName(value: Boolean) = _state.update { it.copy(hideName = value) }
    fun setBiometricOnly(value: Boolean) = _state.update { it.copy(biometricOnly = value) }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            repository.updatePerAppSettings(
                packageName = packageName,
                useCustom = s.useCustom,
                unlockMethod = s.unlockMethod.takeIf { it != "DEFAULT" },
                delayMode = s.delayMode,
                fakeMode = s.fakeMode,
                theme = s.theme,
                hideName = s.hideName,
                biometricOnly = s.biometricOnly,
                tempUnlockMillis = null
            )
            saved.trySend(Unit)
        }
    }

    companion object {
        const val ARG_PACKAGE = "arg_package"
        const val ARG_APP_NAME = "arg_app_name"
    }
}
