package app.lock.photo.valut.features.applock.theme

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.databinding.FragmentLockThemePickerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LockThemePickerActivity : BaseActivity() {

    private lateinit var binding: FragmentLockThemePickerBinding
    private val viewModel: LockThemePickerViewModel by viewModels()
    private lateinit var adapter: LockThemeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentLockThemePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        adapter = LockThemeAdapter(viewModel.themes) { viewModel.select(it) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedTheme.collect { adapter.setSelected(it) }
            }
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, LockThemePickerActivity::class.java)
    }
}
