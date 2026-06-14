package app.lock.photo.valut.features.intruder

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.FragmentIntruderAlertsBinding
import app.lock.photo.valut.features.auth.VerifyMasterActivity
import app.lock.photo.valut.features.intruder.adapter.IntruderAlertsAdapter
import app.lock.photo.valut.features.intruder.model.IntruderAttemptUiModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class IntruderAlertsFragment : Fragment() {

    private var _binding: FragmentIntruderAlertsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: IntruderAlertsViewModel by viewModels()
    private lateinit var adapter: IntruderAlertsAdapter

    private var pendingAction: (() -> Unit)? = null
    private val verifyLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val action = pendingAction
        pendingAction = null
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.markVerified()
            action?.invoke()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIntruderAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener {
            if (viewModel.selectionMode) viewModel.clearSelection()
            else requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.toolbar.inflateMenu(R.menu.menu_intruder_alerts)
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_intruder_settings -> { (requireActivity() as IntruderActivity).openSettings(); true }
                R.id.action_clear_all -> { confirmClearAll(); true }
                else -> false
            }
        }
        adapter = IntruderAlertsAdapter(
            onClick = ::onItemClick,
            onLongClick = { viewModel.toggleSelection(it.id) },
            loadThumbnail = ::loadThumbnail
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.btnDeleteSelected.setOnClickListener { confirmDeleteSelected() }
        observe()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.items.collect { items ->
                        adapter.submitList(items)
                        binding.emptyState.isVisible = items.isEmpty()
                    }
                }
                launch {
                    viewModel.selectionCount.collect { count ->
                        binding.btnDeleteSelected.isVisible = count > 0
                        binding.toolbar.title = if (count > 0) {
                            getString(R.string.selection_count, count)
                        } else {
                            getString(R.string.intruder_alerts_title)
                        }
                    }
                }
            }
        }
    }

    private fun onItemClick(item: IntruderAttemptUiModel) {
        if (viewModel.selectionMode) {
            viewModel.toggleSelection(item.id)
        } else {
            startActivity(IntruderDetailActivity.intent(requireContext(), item.id))
        }
    }

    private fun loadThumbnail(id: Long, imageView: ImageView) {
        viewLifecycleOwner.lifecycleScope.launch {
            val bytes = viewModel.loadThumbnail(id) ?: return@launch
            val bitmap = withContext(Dispatchers.Default) {
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
            } ?: return@launch
            if (imageView.tag == id) imageView.setImageBitmap(bitmap)
        }
    }

    private fun confirmDeleteSelected() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.intruder_delete_title)
            .setMessage(R.string.intruder_delete_message)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteSelected() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmClearAll() {
        runVerified {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.intruder_clear_all_title)
                .setMessage(R.string.intruder_clear_all_message)
                .setPositiveButton(R.string.intruder_clear_all) { _, _ -> viewModel.clearAll() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun runVerified(action: () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            if (viewModel.needsVerification()) {
                pendingAction = action
                verifyLauncher.launch(Intent(requireContext(), VerifyMasterActivity::class.java))
            } else {
                action()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
