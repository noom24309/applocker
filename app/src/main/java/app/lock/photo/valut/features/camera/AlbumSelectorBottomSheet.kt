package app.lock.photo.valut.features.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ItemCameraAlbumBinding
import app.lock.photo.valut.databinding.SheetCameraAlbumSelectorBinding
import app.lock.photo.valut.features.camera.model.CameraAlbumUiModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** Lets the user pick the capture destination (Main Vault, an album, or a new folder). */
class AlbumSelectorBottomSheet : BottomSheetDialogFragment() {

    private var _binding: SheetCameraAlbumSelectorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PrivateCameraViewModel by activityViewModels()
    private lateinit var adapter: CameraAlbumAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = SheetCameraAlbumSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val moveMediaId = arguments?.getLong(ARG_MOVE_MEDIA_ID, -1L)?.takeIf { it > 0 }
        val selectedId = viewModel.uiState.value.selectedAlbumId
        adapter = CameraAlbumAdapter(selectedId) { album ->
            if (moveMediaId != null) {
                viewModel.moveCaptureToAlbum(moveMediaId, album.id)
            } else {
                viewModel.selectAlbum(album.id, album.name.takeIf { album.id != null })
            }
            dismiss()
        }
        binding.albumsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.albumsRecycler.adapter = adapter
        binding.btnCreateFolder.setOnClickListener { showCreateFolderDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.albums.collect(adapter::submitList)
            }
        }
    }

    private fun showCreateFolderDialog() {
        val container = FrameLayout(requireContext())
        val input = EditText(requireContext()).apply { hint = getString(R.string.camera_folder_name_hint) }
        val pad = resources.getDimensionPixelSize(R.dimen.space_l)
        container.setPadding(pad, 0, pad, 0)
        container.addView(input)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.camera_create_folder_title)
            .setView(container)
            .setPositiveButton(R.string.create) { _, _ ->
                viewModel.createFolderAndSelect(input.text.toString())
                dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AlbumSelectorBottomSheet"
        private const val ARG_MOVE_MEDIA_ID = "move_media_id"

        /** When [moveMediaId] is set, selecting an album moves that item instead of setting the destination. */
        fun newInstance(moveMediaId: Long? = null) = AlbumSelectorBottomSheet().apply {
            arguments = Bundle().apply { moveMediaId?.let { putLong(ARG_MOVE_MEDIA_ID, it) } }
        }
    }
}

private class CameraAlbumAdapter(
    private val selectedId: Long?,
    private val onClick: (CameraAlbumUiModel) -> Unit
) : ListAdapter<CameraAlbumUiModel, CameraAlbumAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemCameraAlbumBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CameraAlbumUiModel) = with(binding) {
            albumName.text = item.name
            albumCheck.isVisible = item.id == selectedId
            root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemCameraAlbumBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<CameraAlbumUiModel>() {
            override fun areItemsTheSame(old: CameraAlbumUiModel, new: CameraAlbumUiModel) = old.id == new.id
            override fun areContentsTheSame(old: CameraAlbumUiModel, new: CameraAlbumUiModel) = old == new
        }
    }
}
