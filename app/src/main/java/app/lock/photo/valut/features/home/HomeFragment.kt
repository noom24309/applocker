package app.lock.photo.valut.features.home

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.features.applock.AppLockActivity
import app.lock.photo.valut.features.camera.PrivateCameraActivity
import app.lock.photo.valut.features.intruder.IntruderActivity
import app.lock.photo.valut.features.premium.cleanup.StorageAnalyzerActivity
import app.lock.photo.valut.features.premium.documents.PrivateDocumentsActivity
import app.lock.photo.valut.features.vault.VaultActivity
import app.lock.photo.valut.databinding.FragmentHomeBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNavigation()
        observeState()
    }

    private fun setupNavigation() {
        val ctx = requireContext()

        // Header
        binding.btnAlerts.setOnClickListener { startActivity(IntruderActivity.intent(ctx)) }
        binding.btnProfile.setOnClickListener {
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNav)
                ?.let { it.selectedItemId = R.id.nav_settings }
        }

        // Vault overview
        val openVault = View.OnClickListener { startActivity(VaultActivity.intent(ctx)) }
        binding.btnVaultSeeAll.setOnClickListener(openVault)
        binding.cardVaultPhotos.setOnClickListener(openVault)
        binding.cardVaultVideos.setOnClickListener(openVault)
        binding.cardVaultDocuments.setOnClickListener {
            startActivity(PrivateDocumentsActivity.intent(ctx))
        }

        // Quick actions
        binding.cardLockApps.setOnClickListener {
            startActivity(AppLockActivity.lockedAppsIntent(ctx))
        }
        binding.cardImport.setOnClickListener { startActivity(VaultActivity.intent(ctx)) }
        binding.cardPrivateCamera.setOnClickListener {
            startActivity(PrivateCameraActivity.intent(ctx))
        }
        binding.cardViewAlerts.setOnClickListener { startActivity(IntruderActivity.intent(ctx)) }

        // App Lock
        val openAppLock = View.OnClickListener { startActivity(AppLockActivity.intent(ctx)) }
        binding.btnAppLockManage.setOnClickListener(openAppLock)
        binding.cardAppLock.setOnClickListener(openAppLock)

        // Recent alerts
        val openAlerts = View.OnClickListener { startActivity(IntruderActivity.intent(ctx)) }
        binding.btnAlertsViewAll.setOnClickListener(openAlerts)
        binding.cardAlert1.setOnClickListener(openAlerts)
        binding.cardAlert2.setOnClickListener(openAlerts)

        // Storage
        binding.btnStorageAnalyze.setOnClickListener {
            startActivity(StorageAnalyzerActivity.intent(ctx))
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: HomeUiState) {
        // Hero
        binding.tvHeroSubtitle.text = getString(R.string.home_hero_subtitle, state.lockedApps)
        binding.tvHeroAppsLocked.text = state.lockedApps.toString()
        binding.tvHeroPhotos.text = state.photos.toString()
        binding.tvHeroAlerts.text = state.intruderAlerts.toString()

        // Vault overview
        binding.tvOvPhotos.text = state.photos.toString()
        binding.tvOvVideos.text = state.videos.toString()
        binding.tvOvDocuments.text = state.documents.toString()

        // App Lock
        binding.tvAppLockCount.text =
            getString(R.string.home_applock_protected, state.lockedApps)

        // Storage — 5 GB soft cap, matching the dashboard design
        val totalBytes = 5L * 1024 * 1024 * 1024
        val used = state.storageUsedBytes.coerceAtLeast(0)
        val percent = ((used.toDouble() / totalBytes) * 100).roundToInt().coerceIn(0, 100)
        binding.tvStorageUsed.text = getString(
            R.string.home_storage_used,
            Formatter.formatShortFileSize(requireContext(), used),
            Formatter.formatShortFileSize(requireContext(), totalBytes)
        )
        binding.tvStoragePercent.text = getString(R.string.home_storage_percent, percent)
        binding.progressStorage.progress = percent
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
