package app.lock.photo.valut.core.permissions

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over AndroidX [BiometricManager] / [BiometricPrompt] so screens
 * can query availability and show the system prompt without touching the API directly.
 */
@Singleton
class BiometricHelper @Inject constructor() {

    /** True if the device has enrolled biometrics ready to use. */
    fun isBiometricAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the biometric prompt on [activity].
     *
     * @param onSuccess invoked when authentication succeeds.
     * @param onError invoked with a human-readable message on unrecoverable errors
     *                or when the user cancels.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        prompt.authenticate(info)
    }

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_WEAK
    }
}
