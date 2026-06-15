package app.lock.photo.valut.domain.model

import androidx.appcompat.app.AppCompatDelegate

/**
 * User-selected UI theme. Maps to the [AppCompatDelegate] night-mode constant that
 * is applied app-wide via [AppCompatDelegate.setDefaultNightMode].
 */
enum class AppearanceMode(val nightMode: Int) {
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES),
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

    companion object {
        val DEFAULT = SYSTEM

        fun fromStorage(value: String?): AppearanceMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
