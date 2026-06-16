package app.lock.photo.valut.features.premium.notes

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ActivityPrivateNotesBinding
import app.lock.photo.valut.domain.model.NoteListItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PrivateNotesActivity : BaseActivity() {

    private lateinit var binding: ActivityPrivateNotesBinding
    private val viewModel: PrivateNotesViewModel by viewModels()
    private lateinit var adapter: NotesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityPrivateNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        adapter = NotesAdapter(
            onClick = { startActivity(PrivateNoteEditorActivity.intent(this, it.id)) },
            onFavorite = { viewModel.toggleFavorite(it.id) },
            onLongClick = ::confirmDelete
        )
        binding.notesRecycler.layoutManager = LinearLayoutManager(this)
        binding.notesRecycler.adapter = adapter
        binding.searchInput.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }
        binding.fabAdd.setOnClickListener { startActivity(PrivateNoteEditorActivity.intent(this, -1L)) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.notes)
                    binding.emptyText.isVisible = !state.isLoading && state.notes.isEmpty()
                }
            }
        }
    }

    private fun confirmDelete(note: NoteListItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.notes_delete_title)
            .setMessage(R.string.notes_delete_message)
            .setPositiveButton(R.string.delete_label) { _, _ -> viewModel.deleteNote(note.id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        fun intent(context: Context) = Intent(context, PrivateNotesActivity::class.java)
    }
}
