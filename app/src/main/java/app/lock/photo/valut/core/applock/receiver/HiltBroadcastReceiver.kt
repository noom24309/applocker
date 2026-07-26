package app.lock.photo.valut.core.applock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.CallSuper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    /**
     * Runs [block] off the main thread for a broadcast held open with `goAsync()` and **always**
     * releases the [PendingResult] — under a timeout, even when the work hangs.
     *
     * The platform kills the process with `CannotDeliverBroadcastException` if an async broadcast
     * isn't finished inside its window, so nothing here (a stalled DataStore read, a blocked
     * service start, a thrown exception) is allowed to keep that result open. The timeout sits
     * comfortably under the system limit, and the work itself runs on the IO pool so a blocking
     * call can't starve the shared CPU dispatcher.
     */
    protected fun runAsync(
        pending: PendingResult,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        block: suspend () -> Unit
    ) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (withTimeoutOrNull(timeoutMs) { block() } == null) {
                    Log.w(TAG, "${this@HiltBroadcastReceiver.javaClass.simpleName}: work timed out")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "${this@HiltBroadcastReceiver.javaClass.simpleName}: ${t.javaClass.simpleName}")
            } finally {
                // finish() throws if the result was already released (e.g. after a process
                // restart), and an exception here would itself crash the process.
                runCatching { pending.finish() }
            }
        }
    }

    private companion object {
        const val TAG = "AppLockReceiver"

        /** Well inside the platform's async-broadcast window (10s foreground / 60s background). */
        const val DEFAULT_TIMEOUT_MS = 8_000L
    }
}
