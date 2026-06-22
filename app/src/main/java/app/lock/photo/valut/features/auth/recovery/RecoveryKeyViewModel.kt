package app.lock.photo.valut.features.auth.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.security.RecoveryKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Generates and reveals the one-time recovery key. The plaintext is held only in
 * this ViewModel's state for display; only its hash is persisted.
 */
@HiltViewModel
class RecoveryKeyViewModel @Inject constructor(
    private val recoveryKeyManager: RecoveryKeyManager
) : ViewModel() {

    private val _recoveryKey = MutableStateFlow<String?>(null)
    val recoveryKey: StateFlow<String?> = _recoveryKey.asStateFlow()

    fun ensureKey() {
        if (_recoveryKey.value != null) return
        viewModelScope.launch {
            _recoveryKey.value = recoveryKeyManager.generateAndStore()
        }
    }
}
