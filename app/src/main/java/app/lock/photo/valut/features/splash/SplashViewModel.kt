package app.lock.photo.valut.features.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.domain.model.StartDestination
import app.lock.photo.valut.domain.model.UnlockMethod
import app.lock.photo.valut.domain.repository.SettingsRepository
import app.lock.photo.valut.domain.usecase.GetStartDestinationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Splash routing decision: where to go and (for LOCKED) which unlock screen.
 *
 * [needsLanguage] is true only on the very first launch, when the language picker still has to
 * run before [destination] is reached.
 */
data class SplashRoute(
    val destination: StartDestination,
    val unlockMethod: UnlockMethod,
    val needsLanguage: Boolean = false
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val getStartDestination: GetStartDestinationUseCase,
    private val settingsRepository: SettingsRepository,
    private val dataStore: AppSettingsDataStore
) : ViewModel() {

    private val _route = MutableStateFlow<SplashRoute?>(null)
    val route: StateFlow<SplashRoute?> = _route.asStateFlow()

    fun resolveStartDestination() {
        viewModelScope.launch {
            delay(SPLASH_DELAY_MS) // Brief, intentional splash dwell for a premium feel.
            val destination = getStartDestination()
            val method = settingsRepository.unlockMethod.first()
            val needsLanguage = !dataStore.languageSelected.first()
            _route.value = SplashRoute(destination, method, needsLanguage)
        }
    }

    private companion object {
        const val SPLASH_DELAY_MS = 900L
    }
}
