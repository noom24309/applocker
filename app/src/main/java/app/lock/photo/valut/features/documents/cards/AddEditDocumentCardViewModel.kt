package app.lock.photo.valut.features.documents.cards

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.R
import app.lock.photo.valut.domain.model.DocumentCardInput
import app.lock.photo.valut.domain.model.DocumentCardType
import app.lock.photo.valut.domain.model.VaultMode
import app.lock.photo.valut.domain.repository.CardSaveResult
import app.lock.photo.valut.domain.repository.DocumentCardsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddEditCardUiState(
    val type: DocumentCardType = DocumentCardType.ID_CARD,
    val holderName: String = "",
    val documentNumber: String = "",
    val secondaryText: String = "",
    val issuer: String = "",
    val notes: String = "",
    val expiryDate: Long? = null,
    val colorKey: String = "indigo",
    val frontImageUri: Uri? = null,
    val backImageUri: Uri? = null,
    val existingFrontPath: String? = null,
    val existingBackPath: String? = null,
    val removeBack: Boolean = false,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val loaded: Boolean = false
) {
    /** Whether a front image (new or already stored) is present. */
    val hasFront: Boolean get() = frontImageUri != null || existingFrontPath != null
    val hasBack: Boolean get() = !removeBack && (backImageUri != null || existingBackPath != null)
}

sealed interface AddEditCardEvent {
    object Saved : AddEditCardEvent
    data class Error(val res: Int) : AddEditCardEvent
}

@HiltViewModel
class AddEditDocumentCardViewModel @Inject constructor(
    private val repository: DocumentCardsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val vaultMode = VaultMode.REAL
    private val cardId: Long = savedStateHandle.get<Long>(ARG_CARD_ID) ?: -1L
    private val initialType: String? = savedStateHandle.get<String>(ARG_CARD_TYPE)

    private val _state = MutableStateFlow(AddEditCardUiState())
    val state: StateFlow<AddEditCardUiState> = _state.asStateFlow()

    private val _events = Channel<AddEditCardEvent>(Channel.BUFFERED)
    val events: Flow<AddEditCardEvent> = _events.receiveAsFlow()

    init {
        if (cardId > 0) loadExisting() else initNew()
    }

    private fun initNew() {
        val type = DocumentCardType.fromName(initialType)
        _state.value = AddEditCardUiState(
            type = type,
            colorKey = type.defaultColorKey,
            isEditing = false,
            loaded = true
        )
    }

    private fun loadExisting() {
        viewModelScope.launch {
            val detail = repository.getCardDetail(cardId, vaultMode)
            if (detail == null) {
                _events.send(AddEditCardEvent.Error(R.string.card_open_failed))
                _state.update { it.copy(loaded = true) }
                return@launch
            }
            _state.value = AddEditCardUiState(
                type = detail.type,
                holderName = detail.holderName,
                documentNumber = detail.fullNumber,
                secondaryText = detail.secondaryText,
                issuer = detail.issuerText,
                notes = detail.notes,
                expiryDate = detail.expiryDate,
                colorKey = detail.colorKey,
                existingFrontPath = detail.frontImageEncryptedPath,
                existingBackPath = detail.backImageEncryptedPath,
                isEditing = true,
                loaded = true
            )
        }
    }

    fun setHolderName(value: String) = _state.update { it.copy(holderName = value) }
    fun setDocumentNumber(value: String) = _state.update { it.copy(documentNumber = value) }
    fun setSecondaryText(value: String) = _state.update { it.copy(secondaryText = value) }
    fun setIssuer(value: String) = _state.update { it.copy(issuer = value) }
    fun setNotes(value: String) = _state.update { it.copy(notes = value) }
    fun setColor(key: String) = _state.update { it.copy(colorKey = key) }
    fun setExpiry(value: Long?) = _state.update { it.copy(expiryDate = value) }

    fun setFrontImage(uri: Uri) = _state.update { it.copy(frontImageUri = uri) }

    fun setBackImage(uri: Uri) = _state.update {
        it.copy(backImageUri = uri, removeBack = false)
    }

    fun removeBackImage() = _state.update {
        it.copy(backImageUri = null, existingBackPath = null, removeBack = true)
    }

    fun save() {
        val s = _state.value
        if (s.isSaving) return
        // Require at least one meaningful field so empty cards aren't created.
        val hasText = s.holderName.isNotBlank() || s.documentNumber.isNotBlank()
        if (!hasText && !s.hasFront) {
            viewModelScope.launch { _events.send(AddEditCardEvent.Error(R.string.card_error_need_something)) }
            return
        }
        val input = DocumentCardInput(
            id = if (s.isEditing) cardId else null,
            type = s.type,
            holderName = s.holderName,
            documentNumber = s.documentNumber,
            secondaryText = if (s.type.secondaryLabelRes != null) s.secondaryText else "",
            issuer = if (s.type.issuerLabelRes != null) s.issuer else "",
            notes = s.notes,
            expiryDate = s.expiryDate,
            colorKey = s.colorKey,
            frontImageUri = s.frontImageUri,
            backImageUri = s.backImageUri,
            removeBackImage = s.removeBack
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val result = if (s.isEditing) repository.updateCard(input, vaultMode)
            else repository.createCard(input, vaultMode)
            _state.update { it.copy(isSaving = false) }
            when (result) {
                is CardSaveResult.Success -> _events.send(AddEditCardEvent.Saved)
                is CardSaveResult.Failed -> _events.send(AddEditCardEvent.Error(messageFor(result.reason)))
            }
        }
    }

    private fun messageFor(reason: CardSaveResult.Reason): Int = when (reason) {
        CardSaveResult.Reason.IMAGE_FAILED -> R.string.card_error_image_failed
        CardSaveResult.Reason.STORAGE_FULL -> R.string.card_error_no_storage
        CardSaveResult.Reason.NO_KEY,
        CardSaveResult.Reason.ENCRYPT_FAILED,
        CardSaveResult.Reason.DB_FAILED -> R.string.card_error_save_failed
    }

    companion object {
        const val ARG_CARD_ID = "arg_card_id"
        const val ARG_CARD_TYPE = "arg_card_type"
    }
}
