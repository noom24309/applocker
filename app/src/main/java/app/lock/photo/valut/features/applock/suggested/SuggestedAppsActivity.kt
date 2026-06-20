package app.lock.photo.valut.features.applock.suggested

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.core.applock.AppIconCacheManager
import app.lock.photo.valut.databinding.FragmentSuggestedAppsBinding
import app.lock.photo.valut.domain.model.AppCategory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SuggestedAppsActivity : BaseActivity() {

    private lateinit var binding: FragmentSuggestedAppsBinding
    private val viewModel: SuggestedAppsViewModel by viewModels()

    @Inject
    lateinit var iconCacheManager: AppIconCacheManager

    private lateinit var adapter: SuggestedAppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentSuggestedAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        adapter = SuggestedAppsAdapter(
            categoryLabel = { getString(categoryLabel(it.app.category)) },
            onToggle = { viewModel.toggle(it) },
            loadIcon = ::loadIcon
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.btnLockSelected.setOnClickListener { viewModel.lockSelected() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
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
                            this@SuggestedAppsActivity,
                            getString(R.string.applock_suggestions_locked, count),
                            Toast.LENGTH_SHORT
                        ).show()
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        }
    }

    private fun loadIcon(packageName: String, imageView: ImageView) {
        lifecycleScope.launch {
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

    companion object {
        fun intent(context: Context) = Intent(context, SuggestedAppsActivity::class.java)
    }
}
