package app.lock.photo.valut.core.premium

import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Premium tools that will be gated behind Billing in a later phase. */
enum class PremiumFeature {
    DUPLICATE_FINDER,
    LARGE_FILES,
    STORAGE_ANALYZER,
    PRIVATE_NOTES,
    PRIVATE_DOCUMENTS,
    LOCKED_ALBUMS,
    HIDDEN_ALBUMS,
    ADVANCED_SORT
}

/**
 * Single source of truth for premium-feature access. Phase 11 has NO billing — access is
 * driven by a local DataStore flag (`premiumUnlockedLocal`, default true) so the tools are
 * usable now. Phase 12 will swap [isPremiumEnabled] for a real Billing check without
 * touching any caller.
 */
@Singleton
class PremiumFeatureManager @Inject constructor(
    private val dataStore: AppSettingsDataStore
) {

    /** Reactive premium flag for UI (badges, upgrade placeholders). */
    val premiumEnabled: Flow<Boolean> = dataStore.premiumUnlockedLocal

    suspend fun isPremiumEnabled(): Boolean = dataStore.premiumUnlockedLocal.first()

    /** Every Phase-11 tool is a premium feature; kept as a hook for future free/paid splits. */
    fun isFeaturePremium(feature: PremiumFeature): Boolean = true

    /** True if the user may use [feature] right now. */
    suspend fun canUseFeature(feature: PremiumFeature): Boolean =
        !isFeaturePremium(feature) || isPremiumEnabled()
}
