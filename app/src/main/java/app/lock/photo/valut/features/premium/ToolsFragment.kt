package app.lock.photo.valut.features.premium

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

            AperoNextGenInterstitial.InterAdShowWithCounter(
                activity!!,
                "MainAd",
                enabled = RemoteConfig.interHome && RemoteConfig.enableAllAds,
                callback = object : AperoNextGenAdCallback {
                    override fun onNextAction() {
                        startActivity(
                            DuplicateFinderActivity.intent(
                                requireContext()
                            )
                        )
                    }
                },
                logTag = "MainAd",
                forceShow = false
            )

        }
        binding.cardLargeFiles.setOnClickListener {

            AperoNextGenInterstitial.InterAdShowWithCounter(
                activity!!,
                "MainAd",
                enabled = RemoteConfig.interHome && RemoteConfig.enableAllAds,
                callback = object : AperoNextGenAdCallback {
                    override fun onNextAction() {
                        startActivity(
                            LargeFilesActivity.intent(
                                requireContext()
                            )
                        )
                    }
                },
                logTag = "MainAd",
                forceShow = false
            )

        }
        binding.cardStorage.setOnClickListener {

            AperoNextGenInterstitial.InterAdShowWithCounter(
                activity!!,
                "MainAd",
                enabled = RemoteConfig.interHome && RemoteConfig.enableAllAds,
                callback = object : AperoNextGenAdCallback {
                    override fun onNextAction() {
                        startActivity(
                            StorageAnalyzerActivity.intent(
                                requireContext()
                            )
                        )
                    }
                },
                logTag = "MainAd",
                forceShow = false
            )

        }

        // Security & privacy.
        binding.cardHealth.setOnClickListener {
            AperoNextGenInterstitial.InterAdShowWithCounter(
                activity!!,
                "MainAd",
                enabled = RemoteConfig.interHome && RemoteConfig.enableAllAds,
                callback = object : AperoNextGenAdCallback {
                    override fun onNextAction() {
                        startActivity(
                            VaultHealthActivity.intent(
                                requireContext()
                            )
                        )
                    }
                },
                logTag = "MainAd",
                forceShow = false
            )

        }

        // Private storage.
        binding.cardPrivateNotes.setOnClickListener {

            AperoNextGenInterstitial.InterAdShowWithCounter(
                activity!!,
                "MainAd",
                enabled = RemoteConfig.interHome && RemoteConfig.enableAllAds,
                callback = object : AperoNextGenAdCallback {
                    override fun onNextAction() {
                        startActivity(PrivateNotesActivity.intent(requireContext()))
                    }
                },
                logTag = "MainAd",
                forceShow = false
            )
        }

        // Private storage.
        binding.cardPrivateDocuments.setOnClickListener {
            AperoNextGenInterstitial.InterAdShowWithCounter(
                activity!!,
                "MainAd",
                enabled = RemoteConfig.interHome && RemoteConfig.enableAllAds,
                callback = object : AperoNextGenAdCallback {
                    override fun onNextAction() {
                        startActivity(PrivateDocumentsActivity.intent(requireContext()))
                    }
                },
                logTag = "MainAd",
                forceShow = false
            )
        }


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
