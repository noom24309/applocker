package app.lock.photo.valut.features.premium.documents

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.data.local.entity.PrivateDocumentEntity
import app.lock.photo.valut.databinding.ActivityPrivateDocumentsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PrivateDocumentsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivateDocumentsBinding
    private val viewModel: PrivateDocumentsViewModel by viewModels()
    private lateinit var adapter: DocumentsAdapter

    private var pendingExportId: Long = -1L

    private val pickDocuments = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.importDocuments(uris) }

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> if (uri != null && pendingExportId > 0) viewModel.exportTo(pendingExportId, uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityPrivateDocumentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        adapter = DocumentsAdapter(
            onClick = ::showActions,
            onFavorite = { viewModel.toggleFavorite(it.id) },
            onLongClick = ::confirmDelete
        )
        binding.docsRecycler.layoutManager = LinearLayoutManager(this)
        binding.docsRecycler.adapter = adapter
        binding.searchInput.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }
        binding.fabImport.setOnClickListener { pickDocuments.launch(arrayOf("*/*")) }

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        adapter.submitList(state.documents)
                        binding.emptyText.isVisible = !state.isLoading && state.documents.isEmpty()
                        binding.importProgress.isVisible = state.isImporting
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is DocumentsEvent.Message -> toast(getString(event.res))
                            is DocumentsEvent.ExportReady -> {
                                pendingExportId = event.id
                                createDocument.launch(event.displayName)
                            }
                            is DocumentsEvent.TextPreview -> showTextPreview(event.displayName, event.text)
                        }
                    }
                }
            }
        }
    }

    private fun showActions(doc: PrivateDocumentEntity) {
        val actions = buildList {
            if (viewModel.isTextDocument(doc)) add(getString(R.string.documents_open_text))
            add(getString(R.string.documents_export))
            add(getString(R.string.delete_label))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(doc.displayName)
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    getString(R.string.documents_open_text) -> viewModel.requestTextPreview(doc)
                    getString(R.string.documents_export) -> confirmExport(doc)
                    getString(R.string.delete_label) -> confirmDelete(doc)
                }
            }
            .show()
    }

    private fun confirmExport(doc: PrivateDocumentEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.documents_export)
            .setMessage(R.string.documents_export_warning)
            .setPositiveButton(R.string.documents_export) { _, _ -> viewModel.requestExport(doc) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(doc: PrivateDocumentEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.documents_delete_title)
            .setMessage(R.string.documents_delete_message)
            .setPositiveButton(R.string.delete_label) { _, _ -> viewModel.deleteDocument(doc.id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showTextPreview(name: String, text: String) {
        val pad = resources.getDimensionPixelSize(R.dimen.screen_padding)
        val textView = TextView(this).apply {
            setText(text)
            setTextIsSelectable(false)
            setPadding(pad, pad, pad, pad)
        }
        val scroll = ScrollView(this).apply { addView(textView) }
        MaterialAlertDialogBuilder(this)
            .setTitle(name)
            .setView(scroll)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        fun intent(context: Context) = Intent(context, PrivateDocumentsActivity::class.java)
    }
}
