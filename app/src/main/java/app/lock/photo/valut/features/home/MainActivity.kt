package app.lock.photo.valut.features.home

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import app.lock.photo.valut.R
import app.lock.photo.valut.core.lock.AppLockStateManager
import app.lock.photo.valut.core.lock.LockRouter
import app.lock.photo.valut.core.storage.SecureCacheManager
import app.lock.photo.valut.databinding.ActivityMainBinding
import app.lock.photo.valut.domain.repository.SettingsRepository
import app.lock.photo.valut.domain.repository.VaultRepository
import app.lock.photo.valut.features.premium.ToolsFragment
import app.lock.photo.valut.features.vault.EncryptionMigrationActivity
import app.lock.photo.valut.features.vault.VaultHomeFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Host activity for the post-unlock experience. Shows the Home, Vault and Tools tabs as
 * fragments, switched via a custom bottom bar (no BottomNavigationView). The whole surface
 * is FLAG_SECURE because the Vault tab renders private media thumbnails.
 */
@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject lateinit var appLockStateManager: AppLockStateManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var secureCacheManager: SecureCacheManager
    @Inject lateinit var vaultRepository: VaultRepository

    // Applies its own per-view insets (fragment top + bottom-nav bottom) below.
    override val applyEdgeToEdgeInsets: Boolean = false

    private var currentTab = Tab.HOME

    /** Pre-Phase-4 plain files are migrated to encrypted storage once, on first Vault open. */
    private var migrationChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.navHome.setOnClickListener { selectTab(Tab.HOME) }
        binding.navVault.setOnClickListener { selectTab(Tab.VAULT) }
        binding.navTools.setOnClickListener { selectTab(Tab.TOOLS) }

        if (savedInstanceState == null) {
            selectTab(Tab.HOME)
        } else {
            currentTab = runCatching {
                Tab.valueOf(savedInstanceState.getString(STATE_TAB, Tab.HOME.name))
            }.getOrDefault(Tab.HOME)
            updateNavSelection()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, currentTab.name)
    }

    override fun onResume() {
        super.onResume()
        // Never expose the post-unlock surface while locked. Use the session-locked check
        // (not the full auto-lock policy) so returning from a viewer/sub-screen doesn't re-lock.
        lifecycleScope.launch {
            if (appLockStateManager.isSessionLocked()) {
                appLockStateManager.markLocked()
                startActivity(
                    LockRouter.lockIntent(this@MainActivity, settingsRepository.unlockMethod.first())
                )
                finish()
                return@launch
            }
            runCatching { secureCacheManager.clearAllDecryptedTempFiles() }
        }
    }

    private fun selectTab(tab: Tab) {
        val hasFragment = supportFragmentManager.findFragmentById(binding.fragmentContainer.id) != null
        if (tab != currentTab || !hasFragment) {
            currentTab = tab
            showFragment(
                when (tab) {
                    Tab.HOME -> HomeFragment()
                    Tab.VAULT -> VaultHomeFragment()
                    Tab.TOOLS -> ToolsFragment()
                }
            )
        }
        if (tab == Tab.VAULT) maybeStartMigration()
        updateNavSelection()
    }

    /** If pre-Phase-4 plain files remain, route to the encryption screen before browsing. */
    private fun maybeStartMigration() {
        if (migrationChecked) return
        migrationChecked = true
        lifecycleScope.launch {
            val unencrypted = vaultRepository.observeUnencryptedCount().first()
            if (unencrypted > 0) {
                startActivity(EncryptionMigrationActivity.intent(this@MainActivity))
            }
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(binding.fragmentContainer.id, fragment)
        }
    }

    private fun updateNavSelection() {
        styleTab(binding.navHomeIcon, binding.navHomeLabel, binding.navHomeIndicator, currentTab == Tab.HOME)
        styleTab(binding.navVaultIcon, binding.navVaultLabel, binding.navVaultIndicator, currentTab == Tab.VAULT)
        styleTab(binding.navToolsIcon, binding.navToolsLabel, binding.navToolsIndicator, currentTab == Tab.TOOLS)
    }

    private fun styleTab(icon: ImageView, label: TextView, indicator: View, selected: Boolean) {
        val color = ContextCompat.getColor(
            this, if (selected) R.color.home_primary else R.color.home_system_icon
        )
        icon.setColorFilter(color)
        label.setTextColor(color)
        label.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        indicator.visibility = if (selected) View.VISIBLE else View.INVISIBLE
    }

    /**
     * Edge-to-edge: content draws behind the system bars, so push the fragment content below
     * the status bar and lift the bottom nav above the navigation bar (with cutout padding).
     */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            insets
        }
    }

    private enum class Tab { HOME, VAULT, TOOLS }

    companion object {
        private const val STATE_TAB = "current_tab"

        fun intent(context: Context) = Intent(context, MainActivity::class.java)
    }
}
