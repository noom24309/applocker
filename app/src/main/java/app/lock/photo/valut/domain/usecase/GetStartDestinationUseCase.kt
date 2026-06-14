package app.lock.photo.valut.domain.usecase

import app.lock.photo.valut.domain.model.StartDestination
import app.lock.photo.valut.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Resolves the initial screen for the splash flow from persisted state. A cold
 * start always requires unlocking when a credential exists (the session is not
 * carried across process death).
 */
class GetStartDestinationUseCase @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): StartDestination {
        if (!repository.onboardingCompleted.first()) return StartDestination.ONBOARDING
        if (!repository.pinCreated.first()) return StartDestination.CREATE_PIN
        return StartDestination.LOCKED
    }
}
