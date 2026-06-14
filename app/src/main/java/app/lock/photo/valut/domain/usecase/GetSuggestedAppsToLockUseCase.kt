package app.lock.photo.valut.domain.usecase

import app.lock.photo.valut.domain.model.AppCategory
import app.lock.photo.valut.domain.model.SuggestedApp
import app.lock.photo.valut.domain.repository.AppLockRepository
import javax.inject.Inject

/**
 * Suggests privacy-sensitive apps to lock by matching installed packages/labels against
 * local keyword lists. Entirely on-device: no installed-apps list is uploaded and no
 * remote API is used. Already-locked apps are excluded.
 */
class GetSuggestedAppsToLockUseCase @Inject constructor(
    private val repository: AppLockRepository
) {

    suspend operator fun invoke(): List<SuggestedApp> {
        val installed = repository.loadInstalledApps()
        val locked = repository.getLockedPackageSet()
        return installed.asSequence()
            .filter { it.packageName !in locked }
            .mapNotNull { app ->
                val category = categoryFor(app.packageName, app.appName) ?: return@mapNotNull null
                SuggestedApp(app.packageName, app.appName, category, app.isSystemApp)
            }
            .sortedBy { it.category.ordinal }
            .toList()
    }

    private fun categoryFor(pkg: String, label: String): AppCategory? {
        val hay = (pkg + " " + label).lowercase()
        return when {
            CATEGORY_KEYWORDS[AppCategory.MESSAGING]!!.any { hay.contains(it) } -> AppCategory.MESSAGING
            CATEGORY_KEYWORDS[AppCategory.SOCIAL]!!.any { hay.contains(it) } -> AppCategory.SOCIAL
            CATEGORY_KEYWORDS[AppCategory.FINANCE]!!.any { hay.contains(it) } -> AppCategory.FINANCE
            CATEGORY_KEYWORDS[AppCategory.GALLERY]!!.any { hay.contains(it) } -> AppCategory.GALLERY
            CATEGORY_KEYWORDS[AppCategory.SHOPPING]!!.any { hay.contains(it) } -> AppCategory.SHOPPING
            CATEGORY_KEYWORDS[AppCategory.BROWSER]!!.any { hay.contains(it) } -> AppCategory.BROWSER
            CATEGORY_KEYWORDS[AppCategory.FILES]!!.any { hay.contains(it) } -> AppCategory.FILES
            CATEGORY_KEYWORDS[AppCategory.SETTINGS]!!.any { hay.contains(it) } -> AppCategory.SETTINGS
            else -> null
        }
    }

    private companion object {
        val CATEGORY_KEYWORDS = mapOf(
            AppCategory.MESSAGING to listOf(
                "whatsapp", "messenger", "telegram", "signal", "wechat", "viber",
                "messaging", "messages", "imo", "snapchat", "discord"
            ),
            AppCategory.SOCIAL to listOf(
                "instagram", "facebook", "twitter", "com.twitter", "tiktok", "linkedin",
                "reddit", "pinterest", "tumblr", "threads"
            ),
            AppCategory.FINANCE to listOf(
                "bank", "paypal", "wallet", "upi", "paytm", "phonepe", "gpay",
                "googlepay", "finance", "stripe", "revolut", "venmo", "cash"
            ),
            AppCategory.GALLERY to listOf(
                "gallery", "photos", "com.google.android.apps.photos", "album", "camera"
            ),
            AppCategory.SHOPPING to listOf(
                "amazon", "ebay", "shop", "aliexpress", "flipkart", "etsy", "shein"
            ),
            AppCategory.BROWSER to listOf(
                "chrome", "browser", "firefox", "opera", "brave", "edge", "duckduckgo"
            ),
            AppCategory.FILES to listOf(
                "files", "filemanager", "file.manager", "documents", "explorer"
            ),
            AppCategory.SETTINGS to listOf(
                "com.android.settings", "com.android.vending", "packageinstaller"
            )
        )
    }
}
