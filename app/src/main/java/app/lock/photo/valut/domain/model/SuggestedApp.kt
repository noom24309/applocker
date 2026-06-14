package app.lock.photo.valut.domain.model

/** Category used to surface "apps worth locking" suggestions (matched locally). */
enum class AppCategory {
    SOCIAL, MESSAGING, GALLERY, FINANCE, SHOPPING, BROWSER, SETTINGS, FILES
}

/** A locally-derived suggestion for an app the user may want to protect. */
data class SuggestedApp(
    val packageName: String,
    val appName: String,
    val category: AppCategory,
    val isSystemApp: Boolean
)
