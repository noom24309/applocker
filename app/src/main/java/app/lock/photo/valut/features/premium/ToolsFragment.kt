package app.lock.photo.valut.features.premium

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.lock.photo.valut.AdsSdk.RemoteConfig
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.FragmentToolsBinding
import app.lock.photo.valut.features.cleanup.duplicates.DuplicateFinderActivity
import app.lock.photo.valut.features.cleanup.health.VaultHealthActivity
import app.lock.photo.valut.features.cleanup.largefiles.LargeFilesActivity
import app.lock.photo.valut.features.cleanup.storage.StorageAnalyzerActivity
import app.lock.photo.valut.features.documents.PrivateDocumentsActivity
import app.lock.photo.valut.features.premium.notes.PrivateNotesActivity
import app.lock.photo.valut.features.settings.SettingsActivity
import com.apero.nextgen.AdsSdk.callback.AperoNextGenAdCallback
import com.apero.nextgen.AdsSdk.interstitial.AperoNextGenInterstitial
import com.apero.nextgen.AdsSdk.nativead.AperoNextGenNativeHelper
import dagger.hilt.android.AndroidEntryPoint

/** Tools tab — cleanup + security tools, hosted inside [app.lock.photo.valut.features.home.MainActivity]. */
@AndroidEntryPoint
class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadNativeAd()
        binding.btnSettings.setOnClickListener {
            startActivity(SettingsActivity.intent(requireContext()))
        }

        // Cleanup tools.
        binding.cardDuplicates.setOnClickListener {
            openWithInterstitial { DuplicateFinderActivity.intent(it) }
        }
        binding.cardLargeFiles.setOnClickListener {
            openWithInterstitial { LargeFilesActivity.intent(it) }
        }
        binding.cardStorage.setOnClickListener {
            openWithInterstitial { StorageAnalyzerActivity.intent(it) }
        }

        // Security & privacy.
        binding.cardHealth.setOnClickListener {
            openWithInterstitial { VaultHealthActivity.intent(it) }
        }

        // Private storage.
        binding.cardPrivateNotes.setOnClickListener {
            openWithInterstitial { PrivateNotesActivity.intent(it) }
        }
        binding.cardPrivateDocuments.setOnClickListener {
            openWithInterstitial { PrivateDocumentsActivity.intent(it) }
        }
    }

    /**
     * Shows the "MainAd" interstitial and opens the target screen afterwards. The ad
     * SDK delivers [onNextAction] on a delayed main-thread post, so the fragment may be
     * detached by the time it fires. We capture the host activity up front and launch
     * from it — never from the fragment — and bail out if the activity has gone away,
     * which is what caused the "Fragment not attached to Activity" crash.
     */
    private fun openWithInterstitial(intentFactory: (Context) -> Intent) {
        val host = activity ?: return
        AperoNextGenInterstitial.InterAdShowWithCounter(
            host,
            "MainAd",
            enabled = RemoteConfig.interHome && RemoteConfig.enableAllAds,
            callback = object : AperoNextGenAdCallback {
                override fun onNextAction() {
                    if (host.isFinishing || host.isDestroyed) return
                    host.startActivity(intentFactory(host))
                }
            },
            logTag = "MainAd",
            forceShow = false
        )
    }


    private fun loadNativeAd() {
        AperoNextGenNativeHelper.loadAndShowNativeAdRuntime(
            activity = activity!!,
            container = binding.flAdNative,
            nativeId = getString(R.string.OB2),
            layoutId = R.layout.native_medium_ad_layout_new,
            canShowAds = RemoteConfig.nativeTools && RemoteConfig.enableAllAds,
            reloadNativeId = getString(R.string.OB2),
            canReloadAds = RemoteConfig.nativePattern && RemoteConfig.enableAllAds
        )
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
