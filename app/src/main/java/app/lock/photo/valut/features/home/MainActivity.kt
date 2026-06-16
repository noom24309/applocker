package app.lock.photo.valut.features.home

import app.lock.photo.valut.core.ui.BaseActivity

import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ActivityMainBinding
import app.lock.photo.valut.features.applock.AppLockActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        if (savedInstanceState == null) {
            showFragment(HomeFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showFragment(HomeFragment())
                    true
                }
                R.id.nav_settings -> {
                    showFragment(SettingsFragment())
                    true
                }
                // App Lock, Vault and Tools open their own screens; keep the
                // current tab selected so returning lands back where we were.
                R.id.nav_app_lock -> {
                    startActivity(AppLockActivity.intent(this))
                    false
                }
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
}
