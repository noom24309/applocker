package app.lock.photo.valut.core.applock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.CallSuper

/**
 * Base class that makes Hilt field injection work for manifest-registered receivers.
 *
 * Hilt performs the `@Inject` field injection inside the generated parent's `onReceive()`.
 * Because [BroadcastReceiver.onReceive] is abstract, a subclass can't call `super.onReceive()`
 * directly — so this concrete (empty) override gives subclasses a real super to call. Every
 * subclass MUST call `super.onReceive(context, intent)` as the first line of its own
 * `onReceive`, or its injected dependencies stay uninitialized.
 */
abstract class HiltBroadcastReceiver : BroadcastReceiver() {
    @CallSuper
    override fun onReceive(context: Context, intent: Intent) {
        // Intentionally empty — Hilt's generated override does the injection.
    }
}
