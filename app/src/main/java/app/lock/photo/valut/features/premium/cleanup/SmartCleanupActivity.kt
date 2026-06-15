package app.lock.photo.valut.features.premium.cleanup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import app.lock.photo.valut.R

/**
 * Placeholder for the Smart Cleanup tool. The [SmartCleanupViewModel] and repository logic
 * already exist; the full UI is wired up in a later Phase 11 iteration.
 */
class SmartCleanupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.cleanup_title)
        setContentView(
            TextView(this).apply {
                text = getString(R.string.coming_next_phase)
                gravity = Gravity.CENTER
                val pad = resources.getDimensionPixelSize(R.dimen.space_l)
                setPadding(pad, pad, pad, pad)
            }
        )
    }

    companion object {
        fun intent(context: Context) = Intent(context, SmartCleanupActivity::class.java)
    }
}
