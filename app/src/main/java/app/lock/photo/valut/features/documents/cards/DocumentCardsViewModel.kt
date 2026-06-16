package app.lock.photo.valut.features.documents.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lock.photo.valut.domain.model.DocumentCardUiModel
import app.lock.photo.valut.domain.model.VaultMode
import app.lock.photo.valut.domain.repository.DocumentCardsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CardsFilter { ALL, FAVORITES, DELETED }

data class DocumentCardsUiState(
    val cards: List<DocumentCardUiModel> = emptyList(),
    val filter: CardsFilter = CardsFilter.ALL,
    val query: String = "",
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DocumentCardsViewModel @Inject constructor(
    private val repository: DocumentCardsRepository
) : ViewModel() {

    private val vaultMode = VaultMode.REAL
    private val filter = MutableStateFlow(CardsFilter.ALL)
    private val query = MutableStateFlow("")

    private val sourceCards = filter.flatMapLatest { f ->
        when (f) {
            CardsFilter.ALL -> repository.observeCards(vaultMode)
            CardsFilter.FAVORITES -> repository.observeFavorites(vaultMode)
            CardsFilter.DELETED -> repository.observeDeleted(vaultMode)
        }
    }

    val uiState: StateFlow<DocumentCardsUiState> =
        combine(sourceCards, filter, query) { cards, f, q ->
            val filtered = if (q.isBlank()) cards else cards.filter { card ->
                card.holderName.contains(q, ignoreCase = true) ||
                    card.issuerText.contains(q, ignoreCase = true) ||
                    card.maskedNumber.contains(q, ignoreCase = true)
            }
            DocumentCardsUiState(cards = filtered, filter = f, query = q, isLoading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentCardsUiState())

    fun setFilter(value: CardsFilter) { filter.value = value }
    fun setQuery(value: String) { query.value = value }

    fun toggleFavorite(id: Long) = viewModelScope.launch { repository.toggleFavorite(id) }
    fun softDelete(id: Long) = viewModelScope.launch { repository.softDeleteCards(listOf(id)) }
    fun restore(id: Long) = viewModelScope.launch { repository.restoreCards(listOf(id)) }
    fun permanentlyDelete(id: Long) = viewModelScope.launch { repository.permanentlyDeleteCards(listOf(id)) }
}
