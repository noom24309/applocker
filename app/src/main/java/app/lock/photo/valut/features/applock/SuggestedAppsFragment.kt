package app.lock.photo.valut.features.applock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.core.applock.AppIconCacheManager
import app.lock.photo.valut.databinding.FragmentSuggestedAppsBinding
import app.lock.photo.valut.domain.model.AppCategory
import app.lock.photo.valut.features.applock.adapter.SuggestedAppsAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SuggestedAppsFragment : Fragment() {

    private var _binding: FragmentSuggestedAppsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SuggestedAppsViewModel by viewModels()

    @Inject
    lateinit var iconCacheManager: AppIconCacheManager

    private lateinit var adapter: SuggestedAppsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSuggestedAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        adapter = SuggestedAppsAdapter(
            categoryLabel = { getString(categoryLabel(it.app.category)) },
            onToggle = { viewModel.toggle(it) },
            loadIcon = ::loadIcon
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.btnLockSelected.setOnClickListener { viewModel.lockSelected() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.items.collect { items ->
                        adapter.submitList(items)
                        binding.emptyText.isVisible = items.isEmpty() && !viewModel.loading.value
                    }
                }
                launch { viewModel.loading.collect { binding.progress.isVisible = it } }
                launch {
                    viewModel.lockedFlow.collect { count ->
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.applock_suggestions_locked, count),
                            Toast.LENGTH_SHORT
                        ).show()
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        }
    }

    private fun loadIcon(packageName: String, imageView: ImageView) {
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { iconCacheManager.getIcon(packageName) }
            if (imageView.tag == packageName && bitmap != null) imageView.setImageBitmap(bitmap)
        }
    }

    private fun categoryLabel(category: AppCategory): Int = when (category) {
        AppCategory.SOCIAL -> R.string.category_social
        AppCategory.MESSAGING -> R.string.category_messaging
        AppCategory.GALLERY -> R.string.category_gallery
        AppCategory.FINANCE -> R.string.category_finance
        AppCategory.SHOPPING -> R.string.category_shopping
        AppCategory.BROWSER -> R.string.category_browser
        AppCategory.SETTINGS -> R.string.category_settings
        AppCategory.FILES -> R.string.category_files
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
