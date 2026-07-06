/*
 * AperoNextGenConsent.kt
 *
 * GDPR/Consent gathering (Google UMP — User Messaging Platform).
 *
 * Flow (jaisa user chahta hai): PEHLE consent gather karo -> agar "can request ads"
 * TRUE aaye TABHI ad loading (SDK init + preloads) shuru karo.
 *
 *   AperoNextGenConsent.gatherConsent(this) { canRequestAds ->
 *     if (canRequestAds) {
 *       // ab AperoNextGen.initialize(...) + preloads chalao
 *     } else {
 *       // consent nahi mila — ads load na karo, seedha app flow
 *     }
 *   }
 *
 * - EEA/UK users ko consent form dikhta hai; baqi geos mein form nahi aata aur
 *   canRequestAds normally true hota hai.
 * - Returning user (pehle se consent de chuka) par form dobara nahi aata — cached.
 * - [onResult] EXACTLY ONCE fire hota hai (form dismiss / not-required / error).
 * - Error par bhi flow ruke na — canRequestAds() ki aakhri value ke sath aage.
 *
 * Testing: [testDeviceId] (logcat "Use ConsentDebugSettings.Builder().addTestDeviceHashedId"
 * se milta hai) + [debugGeography] = EEA/NOT_EEA se form force kar sakte hain.
 */

package com.apero.nextgen.AdsSdk.consent

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.apero.nextgen.AdsSdk.logger.AperoNextGenLogger
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

object AperoNextGenConsent {

  private const val TAG = "CONSENT"

  /** Debug geography — form testing ke liye (production mein NONE). */
  enum class DebugGeography { NONE, EEA, NOT_EEA }

  private val mainHandler = Handler(Looper.getMainLooper())

  @Volatile private var consentInformation: ConsentInformation? = null

  private fun runOnMain(action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
  }

  private fun logD(message: String) = AperoNextGenLogger.d(TAG, message)

  private fun logE(message: String) = AperoNextGenLogger.e(TAG, message)

  // ── Public API ────────────────────────────────────────────────────────────────────

  /**
   * Consent gather karta hai, phir [onResult] mein `canRequestAds` (Boolean) deta hai —
   * TRUE ho to hi ad loading shuru karein.
   *
   * @param testDeviceId debug: logcat se mila hashed test-device id (form testing).
   * @param debugGeography debug: EEA/NOT_EEA se geography force (production = NONE).
   * @param tagForUnderAgeOfConsent true = user under age of consent (personalized ads off).
   */
  fun gatherConsent(
    activity: Activity,
    testDeviceId: String? = null,
    debugGeography: DebugGeography = DebugGeography.NONE,
    tagForUnderAgeOfConsent: Boolean = false,
    onResult: (canRequestAds: Boolean) -> Unit,
  ) {
    val delivered = AtomicBoolean(false)
    fun deliver() {
      if (delivered.compareAndSet(false, true)) {
        val can = canRequestAds()
        logD("Consent flow finished. canRequestAds=$can")
        runOnMain { onResult(can) }
      }
    }

    try {
      val info = UserMessagingPlatform.getConsentInformation(activity.applicationContext)
      consentInformation = info

      val params =
        ConsentRequestParameters.Builder()
          .setTagForUnderAgeOfConsent(tagForUnderAgeOfConsent)
          .apply {
            val debug = buildDebugSettings(activity, testDeviceId, debugGeography)
            if (debug != null) setConsentDebugSettings(debug)
          }
          .build()

      logD("Requesting consent info update…")
      info.requestConsentInfoUpdate(
        activity,
        params,
        {
          // Info update mila — zaroorat ho to form load + show, phir result.
          logD("Consent info updated. status=${info.consentStatus} formAvailable=${info.isConsentFormAvailable}")
          UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
            if (formError != null) {
              logE("Consent form error: ${formError.errorCode} ${formError.message}")
            } else {
              logD("Consent form flow complete.")
            }
            deliver() // form dismiss ya not-required — dono par result
          }
        },
        { requestError ->
          // Info update fail (network waghera) — flow ruke na, aakhri known consent ke sath aage.
          logE("Consent info update failed: ${requestError.errorCode} ${requestError.message}")
          deliver()
        },
      )
    } catch (t: Throwable) {
      logE("Consent gather exception: ${t.message}")
      deliver()
    }
  }

  /**
   * Kya ads request kiye ja sakte hain (consent obtained ya not-required)? Returning
   * users ke liye gatherConsent ke baghair bhi seedha check kar sakte hain.
   */
  fun canRequestAds(): Boolean = consentInformation?.canRequestAds() ?: false

  /** Consent status (UMP): UNKNOWN / REQUIRED / NOT_REQUIRED / OBTAINED. */
  fun consentStatus(): Int =
    consentInformation?.consentStatus ?: ConsentInformation.ConsentStatus.UNKNOWN

  /** Privacy-options button dikhana chahiye? (settings mein "Manage consent" ke liye). */
  fun isPrivacyOptionsRequired(): Boolean =
    consentInformation?.privacyOptionsRequirementStatus ==
      ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

  /** Settings se user ko consent dobara badalne ka form (privacy options). */
  fun showPrivacyOptionsForm(activity: Activity, onDismiss: (() -> Unit)? = null) {
    UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
      if (formError != null) logE("Privacy options error: ${formError.errorCode} ${formError.message}")
      runOnMain { onDismiss?.invoke() }
    }
  }

  /** Debug/testing: stored consent reset (agli baar form dobara aayega). */
  fun reset(context: Context) {
    (consentInformation ?: UserMessagingPlatform.getConsentInformation(context.applicationContext))
      .reset()
    logD("Consent reset.")
  }

  // ── Internals ─────────────────────────────────────────────────────────────────────

  private fun buildDebugSettings(
    activity: Activity,
    testDeviceId: String?,
    debugGeography: DebugGeography,
  ): ConsentDebugSettings? {
    if (testDeviceId.isNullOrBlank() && debugGeography == DebugGeography.NONE) return null
    val builder = ConsentDebugSettings.Builder(activity)
    if (!testDeviceId.isNullOrBlank()) builder.addTestDeviceHashedId(testDeviceId)
    when (debugGeography) {
      DebugGeography.EEA -> builder.setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
      DebugGeography.NOT_EEA -> builder.setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_NOT_EEA)
      DebugGeography.NONE -> {}
    }
    return builder.build()
  }
}
