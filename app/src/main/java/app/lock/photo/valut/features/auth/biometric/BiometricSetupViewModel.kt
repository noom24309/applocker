package app.lock.photo.valut.features.auth.biometric

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.lock.AppLockStateManager
import app.lock.photo.valut.domain.model.UnlockMethod
import app.lock.photo.valut.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BiometricSetupViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appLockStateManager: AppLockStateManager
) : ViewModel() {

    private val done = Channel<Unit>(Channel.BUFFERED)
    val doneFlow = done.receiveAsFlow()

    /** Finishes setup, persisting the biometric choice and unlocking the session. */
    fun finishSetup(biometricEnabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBiometricEnabled(biometricEnabled)
            val base = settingsRepository.unlockMethod.first()
            settingsRepository.setUnlockMethod(UnlockMethod.combine(base, biometricEnabled))
            // Setup just completed — the user is authenticated, so start unlocked.
            appLockStateManager.markUnlocked()
            done.trySend(Unit)
        }
    }
}
