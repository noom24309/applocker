package app.lock.photo.valut.features.intruder

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import app.lock.photo.valut.databinding.ActivityIntruderBinding
import dagger.hilt.android.AndroidEntryPoint

/** Host for the intruder alerts list and intruder settings. FLAG_SECURE throughout. */
@AndroidEntryPoint
class IntruderActivity : BaseActivity() {

    private lateinit var binding: ActivityIntruderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityIntruderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState == null) {
            val openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
            replace(if (openSettings) IntruderSettingsFragment() else IntruderAlertsFragment(), addToBackStack = false)
        }
    }

    fun openSettings() = replace(IntruderSettingsFragment())

    private fun replace(fragment: Fragment, addToBackStack: Boolean = true) {
        supportFragmentManager.commit {
            replace(binding.intruderContainer.id, fragment)
            if (addToBackStack) addToBackStack(null)
        }
    }

    companion object {
        private const val EXTRA_OPEN_SETTINGS = "extra_open_settings"

        fun intent(context: Context, openSettings: Boolean = false): Intent =
            Intent(context, IntruderActivity::class.java)
                .putExtra(EXTRA_OPEN_SETTINGS, openSettings)
    }
}
