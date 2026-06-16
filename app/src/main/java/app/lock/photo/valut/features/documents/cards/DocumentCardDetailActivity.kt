package app.lock.photo.valut.features.documents.cards

import app.lock.photo.valut.core.ui.BaseActivity

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.applock.VerifySessionManager
import app.lock.photo.valut.core.storage.SecureThumbnailLoader
import app.lock.photo.valut.databinding.ActivityDocumentCardDetailBinding
import app.lock.photo.valut.domain.model.DocumentCardColors
import app.lock.photo.valut.domain.model.DocumentCardDetail
import app.lock.photo.valut.domain.model.DocumentNumberMasker
import app.lock.photo.valut.domain.repository.CardImageSide
import app.lock.photo.valut.features.auth.VerifyMasterActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Wallet-style card detail. FLAG_SECURE; full number hidden until master verification. */
@AndroidEntryPoint
class DocumentCardDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityDocumentCardDetailBinding
    private val viewModel: DocumentCardDetailViewModel by viewModels()

    @Inject lateinit var thumbnailLoader: SecureThumbnailLoader
    @Inject lateinit var verifySessionManager: VerifySessionManager

    private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    private var numberRevealed = false
    private var pendingExportSide: CardImageSide? = null
    private var pendingAction: (() -> Unit)? = null

    private val verifyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val action = pendingAction
        pendingAction = null
        if (result.resultCode == RESULT_OK) {
            lifecycleScope.launch { verifySessionManager.markVerified() }
            action?.invoke()
        } else {
            toast(getString(R.string.card_verify_to_show))
        }
    }

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/*")
    ) { uri ->
        val side = pendingExportSide
        pendingExportSide = null
        if (uri != null && side != null) viewModel.exportImage(side, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityDocumentCardDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.actionFavorite.setOnClickListener { viewModel.toggleFavorite() }
        binding.actionEdit.setOnClickListener { openEdit() }
        binding.actionExport.setOnClickListener { onExportClicked() }
        binding.actionDelete.setOnClickListener { confirmDelete() }
        binding.showHideButton.setOnClickListener { onShowHideClicked() }

        observe()
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        if (state.loaded && state.detail == null) {
                            finish(); return@collect
                        }
                        state.detail?.let { render(it) }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is CardDetailEvent.Message -> toast(getString(event.res))
                            CardDetailEvent.Deleted -> finish()
                            CardDetailEvent.Restored -> finish()
                        }
                    }
                }
            }
        }
    }

    private fun render(detail: DocumentCardDetail) {
        val type = detail.type

        // Wallet preview
        val preview = binding.preview
        preview.gradientLayer.setBackgroundResource(DocumentCardColors.gradientFor(detail.colorKey))
        preview.cardTypeIcon.setImageResource(type.iconRes)
        preview.cardWatermark.setImageResource(type.iconRes)
        preview.cardTypeLabel.text = getString(type.displayNameRes).uppercase()
        preview.cardHolderName.text = detail.holderName.ifBlank { getString(type.displayNameRes) }
        val masked = DocumentNumberMasker.mask(detail.fullNumber)
        preview.cardNumber.text = masked
        preview.cardNumber.isVisible = masked.isNotEmpty()
        preview.cardExpiry.text = detail.expiryDate?.let { expiryText(it) }.orEmpty()
        preview.cardExpiry.isVisible = detail.expiryDate != null
        preview.cardFavorite.isVisible = false

        // Favorite action icon
        binding.actionFavorite.setImageResource(
            if (detail.isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        )

        // Images
        binding.frontImageCard.isVisible = detail.frontImageEncryptedPath != null
        detail.frontImageEncryptedPath?.let { path ->
            thumbnailLoader.loadCover(binding.frontImage, true, path, null)
            binding.frontImageCard.setOnClickListener { showImageFullScreen(path) }
        }
        binding.backImageCard.isVisible = detail.backImageEncryptedPath != null
        detail.backImageEncryptedPath?.let { path ->
            thumbnailLoader.loadCover(binding.backImage, true, path, null)
            binding.backImageCard.setOnClickListener { showImageFullScreen(path) }
        }

        // Detail rows
        binding.holderLabel.text = getString(type.holderLabelRes)
        binding.holderValue.text = detail.holderName.ifBlank { "—" }
        binding.rowHolder.isVisible = detail.holderName.isNotBlank()

        binding.numberLabel.text = getString(type.numberLabelRes)
        binding.rowNumber.isVisible = detail.fullNumber.isNotBlank()
        renderNumber(detail.fullNumber)

        type.secondaryLabelRes?.let { binding.secondaryLabel.text = getString(it) }
        binding.secondaryValue.text = detail.secondaryText
        binding.rowSecondary.isVisible = type.secondaryLabelRes != null && detail.secondaryText.isNotBlank()

        type.issuerLabelRes?.let { binding.issuerLabel.text = getString(it) }
        binding.issuerValue.text = detail.issuerText
        binding.rowIssuer.isVisible = type.issuerLabelRes != null && detail.issuerText.isNotBlank()

        binding.expiryValue.text = detail.expiryDate?.let { expiryText(it) }.orEmpty()
        binding.rowExpiry.isVisible = detail.expiryDate != null

        binding.notesValue.text = detail.notes
        binding.rowNotes.isVisible = detail.notes.isNotBlank()

        binding.createdText.text = getString(R.string.card_created_at, dateFormat.format(Date(detail.createdAt)))
        binding.updatedText.text = getString(R.string.card_updated_at, dateFormat.format(Date(detail.updatedAt)))
    }

    private fun renderNumber(fullNumber: String) {
        if (numberRevealed) {
            binding.numberValue.text = fullNumber
            binding.showHideButton.setText(R.string.card_hide_number)
            binding.showHideButton.setIconResource(R.drawable.ic_visibility_off)
        } else {
            binding.numberValue.text = DocumentNumberMasker.mask(fullNumber)
            binding.showHideButton.setText(R.string.card_show_number)
            binding.showHideButton.setIconResource(R.drawable.ic_visibility)
        }
    }

    private fun onShowHideClicked() {
        val fullNumber = viewModel.state.value.detail?.fullNumber ?: return
        if (numberRevealed) {
            numberRevealed = false
            renderNumber(fullNumber)
        } else {
            runVerified {
                numberRevealed = true
                renderNumber(fullNumber)
            }
        }
    }

    private fun onExportClicked() {
        val detail = viewModel.state.value.detail ?: return
        val hasFront = detail.frontImageEncryptedPath != null
        val hasBack = detail.backImageEncryptedPath != null
        when {
            hasFront && hasBack -> {
                val options = arrayOf(
                    getString(R.string.card_export_front),
                    getString(R.string.card_export_back)
                )
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.card_action_export)
                    .setItems(options) { _, which ->
                        confirmExport(if (which == 0) CardImageSide.FRONT else CardImageSide.BACK)
                    }
                    .show()
            }
            hasFront -> confirmExport(CardImageSide.FRONT)
            hasBack -> confirmExport(CardImageSide.BACK)
            else -> toast(getString(R.string.card_export_failed))
        }
    }

    private fun confirmExport(side: CardImageSide) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.card_action_export)
            .setMessage(R.string.card_export_warning)
            .setPositiveButton(R.string.card_action_export) { _, _ ->
                runVerified { launchExport(side) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun launchExport(side: CardImageSide) {
        val detail = viewModel.state.value.detail ?: return
        pendingExportSide = side
        val typeName = getString(detail.type.displayNameRes).replace(' ', '_')
        val sideName = if (side == CardImageSide.FRONT) "front" else "back"
        createDocument.launch("${typeName}_$sideName.jpg")
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.card_delete_title)
            .setMessage(R.string.card_delete_message)
            .setPositiveButton(R.string.delete_label) { _, _ -> viewModel.delete() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openEdit() {
        val id = viewModel.state.value.detail?.id ?: return
        startActivity(AddEditDocumentCardActivity.intentForEdit(this, id))
    }

    private fun showImageFullScreen(encryptedPath: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE
        )
        val view = layoutInflater.inflate(R.layout.dialog_image_viewer, null)
        val image = view.findViewById<ImageView>(R.id.fullImage)
        thumbnailLoader.loadCover(image, true, encryptedPath, null)
        view.findViewById<View>(R.id.closeButton).setOnClickListener { dialog.dismiss() }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun runVerified(action: () -> Unit) {
        lifecycleScope.launch {
            if (verifySessionManager.isVerificationStillValid()) {
                action()
            } else {
                pendingAction = action
                verifyLauncher.launch(Intent(this@DocumentCardDetailActivity, VerifyMasterActivity::class.java))
            }
        }
    }

    private fun expiryText(millis: Long): String =
        SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(Date(millis))

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onCreateOptionsMenu(menu: Menu?): Boolean = super.onCreateOptionsMenu(menu)

    companion object {
        fun intent(context: Context, cardId: Long): Intent =
            Intent(context, DocumentCardDetailActivity::class.java)
                .putExtra(DocumentCardDetailViewModel.ARG_CARD_ID, cardId)
    }
}
