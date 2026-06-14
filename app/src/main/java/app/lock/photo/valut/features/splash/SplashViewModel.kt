package app.lock.photo.valut.features.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/** Splash routing decision: where to go and (for LOCKED) which unlock screen. */
data class SplashRoute(
    val destination: StartDestination,
    val unlockMethod: UnlockMethod
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val getStartDestination: GetStartDestinationUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _route = MutableStateFlow<SplashRoute?>(null)
    val route: StateFlow<SplashRoute?> = _route.asStateFlow()

    fun resolveStartDestination() {
        viewModelScope.launch {
            delay(SPLASH_DELAY_MS) // Brief, intentional splash dwell for a premium feel.
            val destination = getStartDestination()
            val method = settingsRepository.unlockMethod.first()
            _route.value = SplashRoute(destination, method)
        }
    }

    private companion object {
        const val SPLASH_DELAY_MS = 900L
    }
}
