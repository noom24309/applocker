package app.lock.photo.valut.features.home

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
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import app.lock.photo.valut.AdsSdk.RemoteConfig
import app.lock.photo.valut.R
import app.lock.photo.valut.core.applock.AppLockServiceManager
import app.lock.photo.valut.core.lock.AppLockStateManager
import app.lock.photo.valut.core.lock.LockRouter
import app.lock.photo.valut.core.storage.SecureCacheManager
import app.lock.photo.valut.core.storage.VaultEncryptionUpgrader
import app.lock.photo.valut.core.ui.BaseActivity
import app.lock.photo.valut.databinding.ActivityMainBinding
import app.lock.photo.valut.domain.repository.SettingsRepository
import app.lock.photo.valut.domain.repository.VaultRepository
import app.lock.photo.valut.features.applock.apps.AppLockAppsFragment
import app.lock.photo.valut.features.premium.ToolsFragment
import app.lock.photo.valut.features.settings.SettingsFragment
import app.lock.photo.valut.features.vault.EncryptionMigrationActivity
import app.lock.photo.valut.features.vault.VaultHomeFragment
import com.nextgen.ads.banner.NextGenBanner
import com.nextgen.ads.callback.NextGenAdCallback
import com.nextgen.ads.interstitial.NextGenInterstitial
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    @Inject lateinit var appLockServiceManager: AppLockServiceManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var secureCacheManager: SecureCacheManager
    @Inject lateinit var encryptionUpgrader: VaultEncryptionUpgrader
    @Inject lateinit var vaultRepository: VaultRepository

    // Applies its own per-view insets (fragment top + bottom-nav bottom) below.
    override val applyEdgeToEdgeInsets: Boolean = false

    private var currentTab = Tab.APPS

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



        loadBanner()


        NextGenInterstitial.InterAdLoadWithCounter(
            placement = "MainAd",
            // High floor pehle; na bhare to normal unit fallback ban jati hai.
            highAdUnitId = getString(R.string.InterMainHigh),
            lowAdUnitId = getString(R.string.InterMain),
            counter = 2,
            enabled = RemoteConfig.interHome && RemoteConfig.enableAllAds, // TODO: yahan Remote Config ki value pass karein
            logTag = "MainAd",
            callback = null,
        )
        useLightSystemBarIcons()

        binding.navVault.setOnClickListener { selectTab(Tab.VAULT) }
        binding.navApps.setOnClickListener { selectTab(Tab.APPS) }
        binding.navTools.setOnClickListener { selectTab(Tab.TOOLS) }
        binding.navSettings.setOnClickListener { selectTab(Tab.SETTINGS) }

        if (savedInstanceState == null) {
            switchTab(Tab.APPS) // pehla tab bina ad ke
        } else {
            currentTab = runCatching {
                Tab.valueOf(savedInstanceState.getString(STATE_TAB, Tab.APPS.name))
            }.getOrDefault(Tab.APPS)
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
            // Blocking file deletes — keep them off the main thread.
            withContext(Dispatchers.IO) {
                runCatching { secureCacheManager.clearAllDecryptedTempFiles() }
            }
            // Cheapest self-heal there is: every time the user opens the app, make sure
            // protection is actually running for whatever they have locked.
            runCatching { appLockServiceManager.ensureRunning() }
            // Quietly rewrite any vault items still in the slow key format, so no screen ever
            // pays that one-time cost again.
            runCatching { encryptionUpgrader.startIfNeeded() }
        }
    }

    /** Tab click: counter wala inter ad pehle, dismiss/skip par asal switch. */
    private fun selectTab(tab: Tab) {
        if (tab == currentTab) return
        NextGenInterstitial.InterAdShowWithCounter(
            this,
            "MainAd",
            enabled = RemoteConfig.interHome && RemoteConfig.enableAllAds,
            callback = object : NextGenAdCallback {
                override fun onNextAction() {
                    if (!isFinishing && !isDestroyed) switchTab(tab)
                }
            },
            logTag = "MainAd",
            forceShow = false
        )
    }

    private fun switchTab(tab: Tab) {
        currentTab = tab
        showFragment(tab)
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

    /** Tabs are added once and then shown/hidden, so switching back never recreates them. */
    private fun showFragment(tab: Tab) {
        // allowStateLoss: this can be driven by an interstitial's dismiss callback, which
        // arrives on a delayed post and may land after onSaveInstanceState (activity stopped
        // but not finishing/destroyed). A plain commit() would then throw
        // "Can not perform this action after onSaveInstanceState". The selected tab is
        // persisted separately via STATE_TAB, so losing this transaction's state is harmless.
        supportFragmentManager.commit(allowStateLoss = true) {
            Tab.values().forEach { other ->
                if (other != tab) {
                    supportFragmentManager.findFragmentByTag(other.name)?.let { hide(it) }
                }
            }
            val existing = supportFragmentManager.findFragmentByTag(tab.name)
            if (existing == null) {
                add(binding.fragmentContainer.id, newFragment(tab), tab.name)
            } else {
                show(existing)
            }
        }
    }

    private fun newFragment(tab: Tab): Fragment = when (tab) {
        Tab.VAULT -> VaultHomeFragment()
        Tab.APPS -> AppLockAppsFragment.asTab()
        Tab.TOOLS -> ToolsFragment()
        Tab.SETTINGS -> SettingsFragment.asTab()
    }

    private fun updateNavSelection() {
        styleTab(binding.navVaultIcon, binding.navVaultLabel, binding.navVaultIndicator, currentTab == Tab.VAULT)
        styleTab(binding.navAppsIcon, binding.navAppsLabel, binding.navAppsIndicator, currentTab == Tab.APPS)
        styleTab(binding.navToolsIcon, binding.navToolsLabel, binding.navToolsIndicator, currentTab == Tab.TOOLS)
        styleTab(
            binding.navSettingsIcon,
            binding.navSettingsLabel,
            binding.navSettingsIndicator,
            currentTab == Tab.SETTINGS
        )
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

    private enum class Tab { APPS, VAULT, TOOLS, SETTINGS }

    companion object {
        private const val STATE_TAB = "current_tab"

        fun intent(context: Context) = Intent(context, MainActivity::class.java)
    }

    private fun loadBanner() {
        NextGenBanner.loadAndShowBanner(
            activity = this,
            container = binding.frAds,
            bannerId = getString(R.string.BannerHome),
            shimmerLayout = R.layout.layout_banner_control,
            canShowAds = RemoteConfig.bannerHome && RemoteConfig.enableAllAds, // TODO: yahan Remote Config ki value pass karein
            canReloadAds = RemoteConfig.bannerHome&& RemoteConfig.enableAllAds, // TODO: Remote Config — resume par reload + replace
            logTag = "CollapsibleBannerHome",
            retryToLoad = 0
        )
    }





}
