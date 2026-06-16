package app.lock.photo.valut.features.premium.documents.cards

import app.lock.photo.valut.core.ui.BaseActivity

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.lock.photo.valut.R
import app.lock.photo.valut.core.storage.SecureThumbnailLoader
import app.lock.photo.valut.databinding.ActivityAddEditDocumentCardBinding
import app.lock.photo.valut.domain.model.DocumentCardColors
import app.lock.photo.valut.domain.model.DocumentCardType
import app.lock.photo.valut.domain.model.DocumentNumberMasker
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Add / edit a wallet-style document card. FLAG_SECURE; all sensitive input is encrypted on save. */
@AndroidEntryPoint
class AddEditDocumentCardActivity : BaseActivity() {

    private lateinit var binding: ActivityAddEditDocumentCardBinding
    private val viewModel: AddEditDocumentCardViewModel by viewModels()

    @Inject lateinit var thumbnailLoader: SecureThumbnailLoader

    private var populated = false
    private val swatchRings = mutableMapOf<String, View>()
    private val expiryFormat = SimpleDateFormat("MM/yyyy", Locale.getDefault())

    private val pickFront = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.setFrontImage(uri) }

    private val pickBack = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.setBackImage(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityAddEditDocumentCardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.frontImageCard.setOnClickListener { launchImagePicker(front = true) }
        binding.frontReplaceButton.setOnClickListener { launchImagePicker(front = true) }
        binding.backImageCard.setOnClickListener { launchImagePicker(front = false) }
        binding.backRemoveButton.setOnClickListener { viewModel.removeBackImage() }
        binding.saveButton.setOnClickListener { viewModel.save() }

        binding.holderInput.doAfterTextChanged {
            viewModel.setHolderName(it?.toString().orEmpty()); refreshPreview()
        }
        binding.numberInput.doAfterTextChanged {
            viewModel.setDocumentNumber(it?.toString().orEmpty()); refreshPreview()
        }
        binding.secondaryInput.doAfterTextChanged { viewModel.setSecondaryText(it?.toString().orEmpty()) }
        binding.issuerInput.doAfterTextChanged {
            viewModel.setIssuer(it?.toString().orEmpty()); refreshPreview()
        }
        binding.notesInput.doAfterTextChanged { viewModel.setNotes(it?.toString().orEmpty()) }

        binding.expiryInput.setOnClickListener { showExpiryPicker() }
        binding.tilExpiry.setEndIconOnClickListener {
            binding.expiryInput.setText("")
            viewModel.setExpiry(null)
            refreshPreview()
        }

        observe()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        if (state.loaded && !populated) populate(state)
                        renderImages(state)
                        binding.saveProgress.isVisible = state.isSaving
                        binding.saveButton.isEnabled = !state.isSaving
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is AddEditCardEvent.Saved -> {
                                toast(getString(R.string.card_saved)); finish()
                            }
                            is AddEditCardEvent.Error -> toast(getString(event.res))
                        }
                    }
                }
            }
        }
    }

    private fun populate(state: AddEditCardUiState) {
        populated = true
        val type = state.type
        binding.toolbar.title = getString(
            if (state.isEditing) R.string.card_edit_title else R.string.card_add_title
        )
        binding.tilHolder.hint = getString(type.holderLabelRes)
        binding.tilNumber.hint = getString(type.numberLabelRes)

        type.secondaryLabelRes?.let { binding.tilSecondary.hint = getString(it) }
        binding.tilSecondary.isVisible = type.secondaryLabelRes != null
        type.issuerLabelRes?.let { binding.tilIssuer.hint = getString(it) }
        binding.tilIssuer.isVisible = type.issuerLabelRes != null
        binding.backImageCard.isVisible = type.supportsBackImage

        binding.holderInput.setText(state.holderName)
        binding.numberInput.setText(state.documentNumber)
        binding.secondaryInput.setText(state.secondaryText)
        binding.issuerInput.setText(state.issuer)
        binding.notesInput.setText(state.notes)
        state.expiryDate?.let { binding.expiryInput.setText(expiryFormat.format(Date(it))) }

        buildSwatches(state.colorKey)
        refreshPreview()
    }

    private fun buildSwatches(selectedKey: String) {
        binding.colorSwatchRow.removeAllViews()
        swatchRings.clear()
        val size = resources.getDimensionPixelSize(R.dimen.card_swatch_size)
        val margin = resources.getDimensionPixelSize(R.dimen.space_s)
        DocumentCardColors.keys.forEach { key ->
            val container = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = margin
                }
                contentDescription = getString(R.string.cd_card_color)
                isClickable = true
                isFocusable = true
            }
            val fill = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundResource(DocumentCardColors.gradientFor(key))
            }
            val ring = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundResource(R.drawable.bg_color_swatch_selected)
                isVisible = key == selectedKey
            }
            container.addView(fill)
            container.addView(ring)
            container.setOnClickListener { selectColor(key) }
            swatchRings[key] = ring
            binding.colorSwatchRow.addView(container)
        }
    }

    private fun selectColor(key: String) {
        viewModel.setColor(key)
        swatchRings.forEach { (k, ring) -> ring.isVisible = k == key }
        refreshPreview()
    }

    private fun launchImagePicker(front: Boolean) {
        val request = PickVisualMediaRequest.Builder()
            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
            .build()
        if (front) pickFront.launch(request) else pickBack.launch(request)
    }

    /** Renders front/back image tiles from current state (picked Uri or existing encrypted path). */
    private fun renderImages(state: AddEditCardUiState) {
        // Front
        when {
            state.frontImageUri != null -> {
                Glide.with(this).load(state.frontImageUri).centerCrop().into(binding.frontPreview)
                binding.frontEmpty.isVisible = false
            }
            state.existingFrontPath != null -> {
                thumbnailLoader.loadCover(binding.frontPreview, true, state.existingFrontPath, null)
                binding.frontEmpty.isVisible = false
            }
            else -> {
                binding.frontPreview.setImageDrawable(null)
                binding.frontEmpty.isVisible = true
            }
        }
        binding.frontReplaceButton.isVisible = state.hasFront

        // Back
        binding.backImageCard.isVisible = state.type.supportsBackImage
        when {
            state.backImageUri != null -> {
                Glide.with(this).load(state.backImageUri).centerCrop().into(binding.backPreview)
                binding.backEmpty.isVisible = false
            }
            state.existingBackPath != null && !state.removeBack -> {
                thumbnailLoader.loadCover(binding.backPreview, true, state.existingBackPath, null)
                binding.backEmpty.isVisible = false
            }
            else -> {
                binding.backPreview.setImageDrawable(null)
                binding.backEmpty.isVisible = true
            }
        }
        binding.backRemoveButton.isVisible = state.type.supportsBackImage && state.hasBack
        refreshPreview()
    }

    /** Updates the live wallet preview from the current inputs + chosen colour/type. */
    private fun refreshPreview() {
        val state = viewModel.state.value
        val type = state.type
        val preview = binding.preview
        preview.gradientLayer.setBackgroundResource(DocumentCardColors.gradientFor(state.colorKey))
        preview.cardTypeIcon.setImageResource(type.iconRes)
        preview.cardWatermark.setImageResource(type.iconRes)
        preview.cardTypeLabel.text = getString(type.displayNameRes).uppercase()
        preview.cardFavorite.isVisible = false

        val holder = binding.holderInput.text?.toString().orEmpty()
        preview.cardHolderName.text = holder.ifBlank { getString(type.displayNameRes) }
        val masked = DocumentNumberMasker.mask(binding.numberInput.text?.toString())
        preview.cardNumber.text = masked
        preview.cardNumber.isVisible = masked.isNotEmpty()
        val expiry = binding.expiryInput.text?.toString().orEmpty()
        preview.cardExpiry.text = expiry
        preview.cardExpiry.isVisible = expiry.isNotEmpty()
    }

    private fun showExpiryPicker() {
        val calendar = Calendar.getInstance()
        viewModel.state.value.expiryDate?.let { calendar.timeInMillis = it }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                viewModel.setExpiry(picked.timeInMillis)
                binding.expiryInput.setText(expiryFormat.format(picked.time))
                refreshPreview()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        fun intentForNew(context: Context, type: DocumentCardType): Intent =
            Intent(context, AddEditDocumentCardActivity::class.java)
                .putExtra(AddEditDocumentCardViewModel.ARG_CARD_TYPE, type.name)

        fun intentForEdit(context: Context, cardId: Long): Intent =
            Intent(context, AddEditDocumentCardActivity::class.java)
                .putExtra(AddEditDocumentCardViewModel.ARG_CARD_ID, cardId)
    }
}
