package app.lock.photo.valut.domain.model

/**
 * Optional privacy disguise shown instead of the normal lock screen. Selected by the
 * user per app (or as a global default). NONE shows the real lock screen / chosen theme.
 */
enum class FakeMode {
    NONE,
    FAKE_CRASH,
    FAKE_LOADING,
    FAKE_CALCULATOR,
    USE_GLOBAL;

    companion object {
        val GLOBAL_DEFAULT = NONE

        fun fromStorage(value: String?): FakeMode =
            entries.firstOrNull { it.name == value } ?: GLOBAL_DEFAULT
    }
}
