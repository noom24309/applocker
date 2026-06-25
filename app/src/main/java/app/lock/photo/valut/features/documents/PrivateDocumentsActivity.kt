package app.lock.photo.valut.features.documents

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.data.local.entity.PrivateDocumentEntity
import app.lock.photo.valut.databinding.ActivityPrivateDocumentsBinding
import app.lock.photo.valut.domain.model.DocumentCardType
import app.lock.photo.valut.domain.model.DocumentCardUiModel
import app.lock.photo.valut.features.documents.cards.AddEditDocumentCardActivity
import app.lock.photo.valut.features.documents.cards.CardsFilter
import app.lock.photo.valut.features.documents.cards.DocumentCardDetailActivity
import app.lock.photo.valut.features.documents.cards.DocumentCardsAdapter
import app.lock.photo.valut.features.documents.cards.DocumentCardsViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PrivateDocumentsActivity : BaseActivity() {

    private lateinit var binding: ActivityPrivateDocumentsBinding
    private val filesViewModel: PrivateDocumentsViewModel by viewModels()
    private val cardsViewModel: DocumentCardsViewModel by viewModels()

    private lateinit var filesAdapter: DocumentsAdapter
    private lateinit var cardsAdapter: DocumentCardsAdapter

    private var pendingExportId: Long = -1L
    private val tabCards = 0
    private val tabFiles = 1
    private var currentTab = tabCards

    private val pickDocuments = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> filesViewModel.importDocuments(uris) }

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> if (uri != null && pendingExportId > 0) filesViewModel.exportTo(pendingExportId, uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TEMP: screenshots enabled for design capture — restore before release
        // window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityPrivateDocumentsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        setupTabs()
        setupCards()
        setupFiles()

        binding.searchInput.doAfterTextChanged { text ->
            val q = text?.toString().orEmpty()
            if (currentTab == tabCards) cardsViewModel.setQuery(q) else filesViewModel.setQuery(q)
        }
        binding.cardsFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chipFavorites -> CardsFilter.FAVORITES
                R.id.chipDeleted -> CardsFilter.DELETED
                else -> CardsFilter.ALL
            }
            cardsViewModel.setFilter(filter)
        }

        binding.fabAdd.setOnClickListener { showAddSheet() }
        binding.btnAddCard.setOnClickListener { showAddCardTypeSheet() }
        binding.btnImportFileEmpty.setOnClickListener { launchImport() }

        selectTab(tabCards)
        observe()
    }

    private fun setupTabs() {
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.documents_tab_cards))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.documents_tab_files))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = selectTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun selectTab(position: Int) {
        currentTab = position
        val cards = position == tabCards
        binding.cardsContainer.isVisible = cards
        binding.filesContainer.isVisible = !cards
        binding.cardsFilterGroup.isVisible = cards
        binding.searchInput.setText("")
        binding.searchInput.hint = getString(if (cards) R.string.cards_search_hint else R.string.documents_search_hint)
    }

    private fun setupCards() {
        cardsAdapter = DocumentCardsAdapter(
            onClick = ::onCardClicked,
            onFavorite = { cardsViewModel.toggleFavorite(it.id) },
            onLongClick = ::onCardLongClicked
        )
        binding.cardsRecycler.layoutManager = LinearLayoutManager(this)
        binding.cardsRecycler.adapter = cardsAdapter
    }

    private fun setupFiles() {
        filesAdapter = DocumentsAdapter(
            onClick = ::showFileActions,
            onFavorite = { filesViewModel.toggleFavorite(it.id) },
            onLongClick = ::confirmFileDelete
        )
        binding.docsRecycler.layoutManager = LinearLayoutManager(this)
        binding.docsRecycler.adapter = filesAdapter
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    cardsViewModel.uiState.collect { state ->
                        cardsAdapter.submitList(state.cards)
                        renderCardsEmpty(state.cards.isEmpty() && !state.isLoading, state.filter)
                    }
                }
                launch {
                    filesViewModel.uiState.collect { state ->
                        filesAdapter.submitList(state.documents)
                        binding.emptyText.isVisible = !state.isLoading && state.documents.isEmpty()
                        binding.importProgress.isVisible = state.isImporting
                    }
                }
                launch {
                    filesViewModel.events.collect { event ->
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

    // --- Cards ---

    private fun renderCardsEmpty(empty: Boolean, filter: CardsFilter) {
        binding.cardsEmpty.isVisible = empty
        if (!empty) return
        val isAll = filter == CardsFilter.ALL
        binding.cardsEmptyTitle.setText(
            when (filter) {
                CardsFilter.ALL -> R.string.cards_empty_title
                CardsFilter.FAVORITES -> R.string.cards_empty_favorites
                CardsFilter.DELETED -> R.string.cards_empty_deleted
            }
        )
        binding.cardsEmptySubtitle.isVisible = isAll
        binding.btnAddCard.isVisible = isAll
        binding.btnImportFileEmpty.isVisible = isAll
    }

    private fun onCardClicked(card: DocumentCardUiModel) {
        if (cardsViewModel.uiState.value.filter == CardsFilter.DELETED) {
            showDeletedCardMenu(card)
        } else {
            startActivity(DocumentCardDetailActivity.intent(this, card.id))
        }
    }

    private fun onCardLongClicked(card: DocumentCardUiModel) {
        if (cardsViewModel.uiState.value.filter == CardsFilter.DELETED) {
            showDeletedCardMenu(card)
        } else {
            MaterialAlertDialogBuilder(this)
                .setItems(arrayOf(getString(R.string.delete_label))) { _, _ ->
                    cardsViewModel.softDelete(card.id)
                }
                .show()
        }
    }

    private fun showDeletedCardMenu(card: DocumentCardUiModel) {
        val options = arrayOf(getString(R.string.card_restore), getString(R.string.card_delete_forever))
        MaterialAlertDialogBuilder(this)
            .setItems(options) { _, which ->
                if (which == 0) cardsViewModel.restore(card.id) else confirmDeleteForever(card)
            }
            .show()
    }

    private fun confirmDeleteForever(card: DocumentCardUiModel) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.card_delete_forever)
            .setMessage(R.string.card_delete_forever_message)
            .setPositiveButton(R.string.card_delete_forever) { _, _ -> cardsViewModel.permanentlyDelete(card.id) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // --- Add sheet ---

    private fun showAddSheet() {
        val sheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.sheet_add_document, null)
        val container = view.findViewById<LinearLayout>(R.id.optionsContainer)
        DocumentCardType.entries.forEach { type ->
            container.addView(buildOption(container, type.iconRes, getString(type.displayNameRes)) {
                sheet.dismiss()
                if (binding.tabs.selectedTabPosition != tabCards) binding.tabs.getTabAt(tabCards)?.select()
                startActivity(AddEditDocumentCardActivity.intentForNew(this, type))
            })
        }
        container.addView(buildOption(container, R.drawable.ic_import, getString(R.string.add_import_file)) {
            sheet.dismiss()
            launchImport()
        })
        sheet.setContentView(view)
        // Let the sheet's own rounded-top background show (the default container is opaque white).
        sheet.setOnShowListener {
            (view.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        sheet.show()
    }

    /** Sheet variant offering only card types (from the empty-state button). */
    private fun showAddCardTypeSheet() = showAddSheet()

    private fun buildOption(parent: LinearLayout, iconRes: Int, title: String, onClick: () -> Unit): View {
        val row = LayoutInflater.from(this).inflate(R.layout.item_add_option, parent, false)
        row.findViewById<ImageView>(R.id.optionIcon).setImageResource(iconRes)
        row.findViewById<TextView>(R.id.optionTitle).text = title
        row.setOnClickListener { onClick() }
        return row
    }

    private fun launchImport() {
        if (binding.tabs.selectedTabPosition != tabFiles) binding.tabs.getTabAt(tabFiles)?.select()
        pickDocuments.launch(arrayOf("*/*"))
    }

    // --- Files (existing behaviour, unchanged logic) ---

    private fun showFileActions(doc: PrivateDocumentEntity) {
        val actions = buildList {
            if (filesViewModel.isTextDocument(doc)) add(getString(R.string.documents_open_text))
            add(getString(R.string.documents_export))
            add(getString(R.string.delete_label))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(doc.displayName)
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    getString(R.string.documents_open_text) -> filesViewModel.requestTextPreview(doc)
                    getString(R.string.documents_export) -> confirmFileExport(doc)
                    getString(R.string.delete_label) -> confirmFileDelete(doc)
                }
            }
            .show()
    }

    private fun confirmFileExport(doc: PrivateDocumentEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.documents_export)
            .setMessage(R.string.documents_export_warning)
            .setPositiveButton(R.string.documents_export) { _, _ -> filesViewModel.requestExport(doc) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmFileDelete(doc: PrivateDocumentEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.documents_delete_title)
            .setMessage(R.string.documents_delete_message)
            .setPositiveButton(R.string.delete_label) { _, _ -> filesViewModel.deleteDocument(doc.id) }
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
