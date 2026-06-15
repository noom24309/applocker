package app.lock.photo.valut.features.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.lock.LockRouter
import app.lock.photo.valut.features.applock.AppLockActivity
import app.lock.photo.valut.features.intruder.IntruderActivity
import app.lock.photo.valut.features.vault.VaultActivity
import app.lock.photo.valut.databinding.FragmentHomeBinding
import app.lock.photo.valut.databinding.ViewStatContentBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
        setupStaticCards()
        binding.cardLockNow.setOnClickListener { viewModel.lockNow() }
        binding.cardAppLock.setOnClickListener {
            startActivity(AppLockActivity.intent(requireContext()))
        }
        binding.cardLockedApps.setOnClickListener {
            startActivity(AppLockActivity.lockedAppsIntent(requireContext()))
        }
        binding.cardIntruder.setOnClickListener {
            startActivity(IntruderActivity.intent(requireContext()))
        }
        binding.cardPrivatePhotos.setOnClickListener {
            startActivity(VaultActivity.intent(requireContext()))
        }
        observeState()
        observeLockNow()
    }

    private fun observeLockNow() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lockNowFlow.collect { method ->
                    startActivity(LockRouter.lockIntent(requireContext(), method))
                }
            }
        }
    }

    private fun setupStaticCards() {
        bindStatLabel(binding.statLockedApps, R.drawable.ic_lock, R.string.home_locked_apps)
        bindStatLabel(binding.statPhotos, R.drawable.ic_photo, R.string.home_photos)
        bindStatLabel(binding.statVideos, R.drawable.ic_video, R.string.home_videos)
        bindStatLabel(binding.statIntruders, R.drawable.ic_intruder, R.string.home_intruders)
    }

    private fun bindStatLabel(stat: ViewStatContentBinding, iconRes: Int, labelRes: Int) {
        stat.statIcon.setImageResource(iconRes)
        stat.statLabel.setText(labelRes)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.statLockedApps.statCount.text = state.lockedApps.toString()
                    binding.statPhotos.statCount.text = state.photos.toString()
                    binding.statVideos.statCount.text = state.videos.toString()
                    binding.statIntruders.statCount.text = state.intruderAlerts.toString()
                    binding.tvIntruderStatus.text = if (state.intruderAlerts > 0) {
                        resources.getQuantityString(
                            R.plurals.intruder_alert_count, state.intruderAlerts, state.intruderAlerts
                        )
                    } else {
                        getString(R.string.intruder_no_alerts)
                    }
                    binding.tvSecurityStatus.setText(R.string.home_security_active)
                    binding.tvAppLockStatus.text = resources.getQuantityString(
                        R.plurals.applock_locked_count, state.lockedApps, state.lockedApps
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
