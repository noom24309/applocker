package app.lock.photo.valut.features.home

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ActivityMainBinding
import app.lock.photo.valut.features.premium.PremiumToolsActivity
import app.lock.photo.valut.features.settings.SettingsFragment
import app.lock.photo.valut.features.vault.VaultActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Host activity for the post-unlock experience. Switches between the home
 * dashboard and settings via the bottom navigation bar.
 */
@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    // Applies its own per-view insets (fragment top + bottom-nav bottom) below.
    override val applyEdgeToEdgeInsets: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        if (savedInstanceState == null) {
            showFragment(HomeFragment())
            if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) showSettings()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showFragment(HomeFragment())
                    true
                }
                // Vault and Tools open their own screens; keep the current tab
                // selected so returning lands back where we were.
                R.id.nav_vault -> {
                    startActivity(VaultActivity.intent(this))
                    false
                }
                R.id.nav_tools -> {
                    startActivity(PremiumToolsActivity.intent(this))
                    false
                }
                else -> false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            binding.bottomNav.selectedItemId = R.id.nav_home
            showSettings()
        }
    }

    /** Opens Settings over the home tab (reachable via the gear on Home). */
    fun showSettings() {
        supportFragmentManager.commit {
            replace(binding.fragmentContainer.id, SettingsFragment())
            addToBackStack(null)
        }
    }

    /**
     * Edge-to-edge: content draws behind the system bars, so push the fragment
     * content below the status bar and lift the bottom nav above the navigation
     * bar (with side padding for display cutouts).
     */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.fragmentContainer.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            binding.bottomNav.updatePadding(bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(binding.fragmentContainer.id, fragment)
        }
    }

    companion object {
        private const val EXTRA_OPEN_SETTINGS = "open_settings"

        /** Returns to the (existing) home host and opens the Settings screen. */
        fun settingsIntent(context: Context) =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_OPEN_SETTINGS, true)
            }
    }
}
