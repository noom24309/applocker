package app.lock.photo.valut.features.applock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import app.lock.photo.valut.databinding.ActivityAppLockBinding
import app.lock.photo.valut.features.applock.model.AppFilter
import dagger.hilt.android.AndroidEntryPoint

/** Host for the App Lock dashboard, installed-apps list and settings. */
@AndroidEntryPoint
class AppLockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppLockBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppLockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState == null) {
            replace(AppLockHomeFragment(), addToBackStack = false)
            // Deep-link from Home's "Locked Apps" card: jump straight to the locked list.
            if (intent.getBooleanExtra(EXTRA_OPEN_LOCKED_APPS, false)) {
                openApps(AppFilter.LOCKED)
            }
        }
    }

    fun openApps(initialFilter: AppFilter? = null) =
        replace(AppLockAppsFragment.newInstance(initialFilter))
    fun openSettings() = replace(AppLockSettingsFragment())
    fun openThemePicker() = replace(LockThemePickerFragment())
    fun openTroubleshooting() = replace(AppLockTroubleshootingFragment())
    fun openSuggestions() = replace(SuggestedAppsFragment())

    private fun replace(fragment: Fragment, addToBackStack: Boolean = true) {
        supportFragmentManager.commit {
            replace(binding.appLockContainer.id, fragment)
            if (addToBackStack) addToBackStack(null)
        }
    }

    companion object {
        private const val EXTRA_OPEN_LOCKED_APPS = "open_locked_apps"

        fun intent(context: Context) = Intent(context, AppLockActivity::class.java)

        /** Opens App Lock straight on the locked-apps list (from Home's stat card). */
        fun lockedAppsIntent(context: Context) =
            intent(context).putExtra(EXTRA_OPEN_LOCKED_APPS, true)
    }
}
