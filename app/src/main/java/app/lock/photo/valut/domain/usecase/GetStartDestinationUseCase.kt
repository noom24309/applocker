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
        // Onboarding done but no master credential yet — first-run path: choose PIN/pattern.
        val hasCredential = repository.pinCreated.first() || repository.patternEnabled.first()
        if (!hasCredential) return StartDestination.SETUP_CREDENTIAL
        // Credential set up: a cold start requires unlocking (pattern/PIN per UnlockMethod).
        // The unlock screen routes straight to home on success.
        return StartDestination.LOCKED
    }
}
