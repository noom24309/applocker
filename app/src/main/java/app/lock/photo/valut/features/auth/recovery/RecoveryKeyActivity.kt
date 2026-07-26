package app.lock.photo.valut.features.auth.recovery
import app.lock.photo.valut.features.auth.biometric.BiometricSetupActivity

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import app.lock.photo.valut.core.ui.showToast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.AdsSdk.RemoteConfig
import app.lock.photo.valut.R
import app.lock.photo.valut.core.lock.LockExempt
import app.lock.photo.valut.databinding.ActivityRecoveryKeyBinding
import com.nextgen.ads.nativead.NextGenNativeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Shows the one-time recovery key. The plaintext is revealed only here; only its
 * hash is stored. Continues to biometric setup.
 */
@AndroidEntryPoint
class RecoveryKeyActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivityRecoveryKeyBinding
    private val viewModel: RecoveryKeyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecoveryKeyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Drawn on the splash background, so the system bars need light icons.
        useLightSystemBarIcons()

        viewModel.ensureKey()
        observeKey()

        loadNativeAd()
        binding.btnCopy.setOnClickListener { copyKey() }
        binding.btnContinue.setOnClickListener {
            startActivity(Intent(this, BiometricSetupActivity::class.java))
            finishAffinity()
        }
    }


    private fun loadNativeAd() {
        NextGenNativeHelper.loadAndShowNativeAdRuntime(
            activity = this,
            container = binding.flAdNative,
            nativeId = getString(R.string.NativeRecoveryKey),
            // Same native layout the splash uses.
            layoutId = R.layout.custom_admob_native_layout_1,
            canShowAds = RemoteConfig.nativeHome&& RemoteConfig.enableAllAds,
            reloadNativeId = getString(R.string.NativeRecoveryKey),
            canReloadAds = RemoteConfig.nativeHome&& RemoteConfig.enableAllAds,
            logTag = "RecoveryKey"
        )
    }

    private fun observeKey() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recoveryKey.collect { key ->
                    binding.tvRecoveryKey.text = key.orEmpty()
                    binding.btnCopy.isEnabled = key != null
                    binding.btnContinue.isEnabled = key != null
                }
            }
        }
    }

    private fun copyKey() {
        val key = binding.tvRecoveryKey.text?.toString().orEmpty()
        if (key.isEmpty()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.recovery_title), key))
        showToast(R.string.recovery_copied)
    }
}
