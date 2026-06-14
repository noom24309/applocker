package app.lock.photo.valut.domain.model

/**
 * Visual style of the App Lock overlay. CALCULATOR / FAKE_CRASH / FAKE_LOADING are
 * disguise themes; the rest are standard unlock looks. A per-app value of [USE_GLOBAL]
 * means "follow the global theme".
 */
enum class LockTheme {
    DEFAULT,
    DARK,
    GLASS,
    MINIMAL,
    CALCULATOR,
    FAKE_CRASH,
    FAKE_LOADING,
    USE_GLOBAL;

    companion object {
        val GLOBAL_DEFAULT = DEFAULT

        fun fromStorage(value: String?): LockTheme =
            entries.firstOrNull { it.name == value } ?: GLOBAL_DEFAULT
    }
}
