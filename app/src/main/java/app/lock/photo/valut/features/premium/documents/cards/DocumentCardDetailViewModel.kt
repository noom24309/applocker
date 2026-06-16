package app.lock.photo.valut.features.premium.documents.cards

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.R
import app.lock.photo.valut.domain.model.DocumentCardDetail
import app.lock.photo.valut.domain.model.VaultMode
import app.lock.photo.valut.domain.repository.CardImageSide
import app.lock.photo.valut.domain.repository.DocumentCardsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DocumentCardDetailUiState(
    val loaded: Boolean = false,
    val detail: DocumentCardDetail? = null
)

sealed interface CardDetailEvent {
    data class Message(val res: Int) : CardDetailEvent
    object Deleted : CardDetailEvent
    object Restored : CardDetailEvent
}

@HiltViewModel
class DocumentCardDetailViewModel @Inject constructor(
    private val repository: DocumentCardsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val vaultMode = VaultMode.REAL
    private val cardId: Long = savedStateHandle.get<Long>(ARG_CARD_ID) ?: -1L

    private val _state = MutableStateFlow(DocumentCardDetailUiState())
    val state: StateFlow<DocumentCardDetailUiState> = _state.asStateFlow()

    private val _events = Channel<CardDetailEvent>(Channel.BUFFERED)
    val events: Flow<CardDetailEvent> = _events.receiveAsFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            val detail = repository.getCardDetail(cardId, vaultMode)
            _state.value = DocumentCardDetailUiState(loaded = true, detail = detail)
            if (detail == null) _events.send(CardDetailEvent.Message(R.string.card_open_failed))
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            repository.toggleFavorite(cardId)
            reload()
        }
    }

    fun delete() {
        viewModelScope.launch {
            repository.softDeleteCards(listOf(cardId))
            _events.send(CardDetailEvent.Deleted)
        }
    }

    fun restore() {
        viewModelScope.launch {
            repository.restoreCards(listOf(cardId))
            _events.send(CardDetailEvent.Restored)
        }
    }

    fun permanentlyDelete() {
        viewModelScope.launch {
            repository.permanentlyDeleteCards(listOf(cardId))
            _events.send(CardDetailEvent.Deleted)
        }
    }

    fun exportImage(side: CardImageSide, destUri: Uri) {
        viewModelScope.launch {
            val ok = repository.exportCardImage(cardId, side, destUri, vaultMode)
            _events.send(
                CardDetailEvent.Message(if (ok) R.string.card_export_done else R.string.card_export_failed)
            )
        }
    }

    companion object {
        const val ARG_CARD_ID = "arg_card_id"
    }
}
