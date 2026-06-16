package app.lock.photo.valut.features.premium.notes

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ActivityNoteEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Full-screen encrypted note editor. FLAG_SECURE; auto-saves on exit. */
@AndroidEntryPoint
class PrivateNoteEditorActivity : BaseActivity() {

    private lateinit var binding: ActivityNoteEditorBinding
    private val viewModel: PrivateNoteEditorViewModel by viewModels()

    private var populated = false
    private var pendingExportText: String? = null

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { writeExport(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this) { saveAndFinish() }

        binding.bodyInput.doAfterTextChanged { updateMeta() }
        updateMeta()

        observe()
    }

    private fun updateMeta() {
        val text = binding.bodyInput.text?.toString().orEmpty()
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        binding.tvMeta.text = getString(R.string.notes_meta_count, words, text.length)
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        if (state.loaded && !populated) {
                            populated = true
                            binding.titleInput.setText(state.title)
                            binding.bodyInput.setText(state.content)
                            updateMeta()
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is NoteEditorEvent.Saved -> finish()
                            NoteEditorEvent.SaveFailed ->
                                toast(getString(R.string.notes_save_failed))
                            is NoteEditorEvent.Exported -> {
                                pendingExportText = event.text
                                createDocument.launch(exportFileName())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveAndFinish() {
        viewModel.save(
            binding.titleInput.text?.toString().orEmpty(),
            binding.bodyInput.text?.toString().orEmpty()
        )
    }

    private fun writeExport(uri: Uri) {
        val text = pendingExportText ?: return
        val ok = runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            true
        }.getOrDefault(false)
        pendingExportText = null
        toast(getString(if (ok) R.string.notes_export_done else R.string.notes_export_failed))
    }

    private fun exportFileName(): String {
        val title = binding.titleInput.text?.toString()?.trim()?.ifEmpty { "note" } ?: "note"
        return "${title.take(40)}.txt"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        fun intent(context: Context, noteId: Long): Intent =
            Intent(context, PrivateNoteEditorActivity::class.java)
                .putExtra(PrivateNoteEditorViewModel.ARG_NOTE_ID, noteId)
    }
}
