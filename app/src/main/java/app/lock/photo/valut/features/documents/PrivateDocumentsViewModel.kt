package app.lock.photo.valut.features.documents

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.R
import app.lock.photo.valut.data.local.entity.PrivateDocumentEntity
import app.lock.photo.valut.domain.model.DocumentImportResult
import app.lock.photo.valut.domain.model.VaultMode
import app.lock.photo.valut.domain.repository.PrivateDocumentsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivateDocumentsUiState(
    val documents: List<PrivateDocumentEntity> = emptyList(),
    val query: String = "",
    val isImporting: Boolean = false,
    val isLoading: Boolean = true
)

sealed interface DocumentsEvent {
    data class Message(val res: Int) : DocumentsEvent
    data class ExportReady(val id: Long, val displayName: String) : DocumentsEvent
    data class TextPreview(val displayName: String, val text: String) : DocumentsEvent
}

@HiltViewModel
class PrivateDocumentsViewModel @Inject constructor(
    private val repository: PrivateDocumentsRepository
) : ViewModel() {

    private val vaultMode = VaultMode.REAL
    private val query = MutableStateFlow("")
    private val importing = MutableStateFlow(false)

    private val _events = Channel<DocumentsEvent>(Channel.BUFFERED)
    val events: Flow<DocumentsEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<PrivateDocumentsUiState> =
        combine(repository.observeDocuments(vaultMode), query, importing) { docs, q, isImporting ->
            val filtered = if (q.isBlank()) docs else docs.filter {
                it.displayName.contains(q, ignoreCase = true)
            }
            PrivateDocumentsUiState(
                documents = filtered, query = q, isImporting = isImporting, isLoading = false
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivateDocumentsUiState())

    fun setQuery(value: String) { query.value = value }

    fun importDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            var failed = 0
            uris.forEach { uri ->
                if (repository.importDocument(uri, vaultMode) is DocumentImportResult.Failed) failed++
            }
            importing.value = false
            if (failed > 0) _events.send(DocumentsEvent.Message(R.string.docs_import_failed))
            else _events.send(DocumentsEvent.Message(R.string.docs_import_done))
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch { repository.softDeleteDocuments(listOf(id)) }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch { repository.toggleFavorite(id) }
    }

    fun requestExport(doc: PrivateDocumentEntity) {
        viewModelScope.launch { _events.send(DocumentsEvent.ExportReady(doc.id, doc.displayName)) }
    }

    /** True for text documents that can be previewed inside the app. */
    fun isTextDocument(doc: PrivateDocumentEntity): Boolean =
        doc.mimeType.startsWith("text") || doc.displayName.endsWith(".txt", ignoreCase = true)

    fun requestTextPreview(doc: PrivateDocumentEntity) {
        viewModelScope.launch {
            val text = repository.decryptTextPreview(doc.id, TEXT_PREVIEW_CHARS)
            if (text == null) _events.send(DocumentsEvent.Message(R.string.docs_open_failed))
            else _events.send(DocumentsEvent.TextPreview(doc.displayName, text))
        }
    }

    fun exportTo(id: Long, destUri: Uri) {
        viewModelScope.launch {
            val ok = repository.exportDocument(id, destUri)
            _events.send(DocumentsEvent.Message(if (ok) R.string.docs_export_done else R.string.docs_export_failed))
        }
    }

    private companion object {
        const val TEXT_PREVIEW_CHARS = 100_000
    }
}
