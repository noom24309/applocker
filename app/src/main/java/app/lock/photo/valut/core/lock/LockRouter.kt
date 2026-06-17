package app.lock.photo.valut.core.lock

import android.content.Context
import android.content.Intent
import app.lock.photo.valut.domain.model.UnlockMethod
import app.lock.photo.valut.features.auth.PatternUnlockActivity
import app.lock.photo.valut.features.auth.UnlockActivity

/** Chooses the correct unlock screen for the active [UnlockMethod]. */
object LockRouter {

    fun lockIntent(
        context: Context,
        method: UnlockMethod
    ): Intent {
        val target = if (method.usesPattern) {
            PatternUnlockActivity::class.java
        } else {
            UnlockActivity::class.java
        }
        return Intent(context, target)
    }
}
