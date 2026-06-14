package app.lock.photo.valut.features.intruder

import androidx.annotation.StringRes
import app.lock.photo.valut.R
import app.lock.photo.valut.domain.model.IntruderAutoDeleteMode
import app.lock.photo.valut.domain.model.IntruderTrigger

/** String-resource mapping for intruder enums (shared by list/detail/settings). */
object IntruderLabels {

    @StringRes
    fun source(value: String): Int = when (IntruderTrigger.fromStorage(value)) {
        IntruderTrigger.APP_UNLOCK -> R.string.intruder_source_app_unlock
        IntruderTrigger.VAULT_UNLOCK -> R.string.intruder_source_vault
        IntruderTrigger.APP_LOCK_OVERLAY -> R.string.intruder_source_app_lock
        IntruderTrigger.FAKE_CALCULATOR -> R.string.intruder_source_calculator
        IntruderTrigger.FAKE_CRASH -> R.string.intruder_source_fake_crash
        IntruderTrigger.PATTERN_UNLOCK -> R.string.intruder_source_pattern
    }

    @StringRes
    fun autoDelete(mode: IntruderAutoDeleteMode): Int = when (mode) {
        IntruderAutoDeleteMode.NEVER -> R.string.intruder_autodelete_never
        IntruderAutoDeleteMode.DAYS_7 -> R.string.intruder_autodelete_7
        IntruderAutoDeleteMode.DAYS_30 -> R.string.intruder_autodelete_30
        IntruderAutoDeleteMode.DAYS_90 -> R.string.intruder_autodelete_90
    }

    val ATTEMPT_OPTIONS = listOf(1, 2, 3, 5)
    val MAX_RECORD_OPTIONS = listOf(50, 100, 250)
    val AUTO_DELETE_OPTIONS = IntruderAutoDeleteMode.entries.toList()
}
