package app.lock.photo.valut.core.lock

import android.content.Context
import android.content.Intent
import app.lock.photo.valut.domain.model.IntruderTrigger
import app.lock.photo.valut.domain.model.UnlockMethod
import app.lock.photo.valut.features.auth.PatternUnlockActivity
import app.lock.photo.valut.features.auth.UnlockActivity

/** Chooses the correct unlock screen for the active [UnlockMethod]. */
object LockRouter {

    /** Intent extra carrying the [IntruderTrigger] source name for intruder capture. */
    const val EXTRA_TRIGGER_SOURCE = "extra_trigger_source"

    fun lockIntent(
        context: Context,
        method: UnlockMethod,
        source: IntruderTrigger = IntruderTrigger.APP_UNLOCK
    ): Intent {
        val target = if (method.usesPattern) {
            PatternUnlockActivity::class.java
        } else {
            UnlockActivity::class.java
        }
        return Intent(context, target).putExtra(EXTRA_TRIGGER_SOURCE, source.name)
    }
}
