package app.lock.photo.valut.features.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.core.applock.AppIconCacheManager
import app.lock.photo.valut.databinding.FragmentHomeBinding
import app.lock.photo.valut.features.applock.apps.AppLockAppsAdapter
import app.lock.photo.valut.features.applock.apps.AppLockAppsViewModel
import app.lock.photo.valut.features.applock.model.AppFilter
import app.lock.photo.valut.features.applock.perapp.PerAppLockSettingsBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Home tab: the installed-apps list with a per-app lock toggle. This is the app's
 * primary screen — photos, videos and documents live under the Vault tab.
 */
@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppLockAppsViewModel by viewModels()

    @Inject
    lateinit var iconCacheManager: AppIconCacheManager

    private lateinit var adapter: AppLockAppsAdapter

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
        binding.btnSettings.setOnClickListener {
            (requireActivity() as MainActivity).showSettings()
        }
        setupRecycler()
        setupControls()
        observe()
    }

    private fun setupRecycler() {
        adapter = AppLockAppsAdapter(
            onToggle = { app, locked -> viewModel.setLocked(app, locked) },
            onConfigure = { app ->
                PerAppLockSettingsBottomSheet.newInstance(app.packageName, app.appName)
                    .show(childFragmentManager, "perAppLock")
            },
            loadIcon = ::loadIcon
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupControls() {
        binding.searchInput.addTextChangedListener { viewModel.setQuery(it?.toString().orEmpty()) }
        binding.chipAll.setOnClickListener { viewModel.setFilter(AppFilter.ALL) }
        binding.chipLocked.setOnClickListener { viewModel.setFilter(AppFilter.LOCKED) }
        binding.chipUnlocked.setOnClickListener { viewModel.setFilter(AppFilter.UNLOCKED) }
        binding.chipSystem.setOnClickListener { viewModel.toggleSystemApps() }
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.apps.collect { apps ->
                        adapter.submitList(apps)
                        binding.emptyText.isVisible = apps.isEmpty() && !viewModel.loading.value
                    }
                }
                launch {
                    viewModel.loading.collect { loading ->
                        binding.progress.isVisible = loading
                    }
                }
                launch {
                    viewModel.showSystemApps.collect { binding.chipSystem.isChecked = it }
                }
            }
        }
    }

    private fun loadIcon(packageName: String, imageView: ImageView) {
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { iconCacheManager.getIcon(packageName) }
            if (imageView.tag == packageName && bitmap != null) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
