package app.lock.photo.valut.domain.model

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.lock.photo.valut.R

/**
 * The kinds of wallet-style document cards a user can store. Each type carries its own
 * display name, icon, default colour and the labels for its editable fields. The fields map
 * onto the fixed encrypted columns of PrivateDocumentCardEntity (holder / number / secondary
 * / issuer), so adding a type never needs a schema change.
 */
enum class DocumentCardType(
    @StringRes val displayNameRes: Int,
    @DrawableRes val iconRes: Int,
    val defaultColorKey: String,
    @StringRes val holderLabelRes: Int,
    @StringRes val numberLabelRes: Int,
    /** Optional second free-text field (e.g. institute / company / provider). Null = hidden. */
    @StringRes val secondaryLabelRes: Int?,
    /** Optional issuer field (country / state / nationality). Null = hidden. */
    @StringRes val issuerLabelRes: Int?,
    val supportsBackImage: Boolean
) {
    ID_CARD(
        R.string.card_type_id, R.drawable.ic_card_id, "indigo",
        R.string.card_field_holder_name, R.string.card_field_id_number,
        R.string.card_field_date_of_birth, R.string.card_field_country, supportsBackImage = true
    ),
    DRIVING_LICENCE(
        R.string.card_type_licence, R.drawable.ic_card_licence, "teal",
        R.string.card_field_holder_name, R.string.card_field_licence_number,
        null, R.string.card_field_country_state, supportsBackImage = true
    ),
    PASSPORT(
        R.string.card_type_passport, R.drawable.ic_card_passport, "blue",
        R.string.card_field_holder_name, R.string.card_field_passport_number,
        null, R.string.card_field_nationality, supportsBackImage = false
    ),
    STUDENT_CARD(
        R.string.card_type_student, R.drawable.ic_card_student, "amber",
        R.string.card_field_student_name, R.string.card_field_student_id,
        R.string.card_field_institute_name, null, supportsBackImage = true
    ),
    WORK_CARD(
        R.string.card_type_work, R.drawable.ic_card_work, "slate",
        R.string.card_field_employee_name, R.string.card_field_employee_id,
        R.string.card_field_company_name, null, supportsBackImage = true
    ),
    VEHICLE_REGISTRATION(
        R.string.card_type_vehicle, R.drawable.ic_card_vehicle, "green",
        R.string.card_field_owner_name, R.string.card_field_vehicle_number,
        R.string.card_field_registration_number, null, supportsBackImage = true
    ),
    INSURANCE_CARD(
        R.string.card_type_insurance, R.drawable.ic_card_insurance, "purple",
        R.string.card_field_holder_name, R.string.card_field_policy_number,
        R.string.card_field_provider_name, null, supportsBackImage = true
    ),
    OTHER(
        R.string.card_type_other, R.drawable.ic_card_other, "rose",
        R.string.card_field_title, R.string.card_field_number,
        null, null, supportsBackImage = true
    );

    companion object {
        /** Safe parse: unknown / null names fall back to [OTHER] so a card never crashes the UI. */
        fun fromName(name: String?): DocumentCardType =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/**
 * Premium card colour themes. Each key maps to a gradient drawable that reads well in both
 * light and dark mode (the card is a coloured gradient with white text, independent of theme).
 */
object DocumentCardColors {

    /** Ordered keys shown in the colour picker. */
    val keys: List<String> = listOf(
        "indigo", "blue", "teal", "green", "amber", "purple", "slate", "rose"
    )

    private val gradients: Map<String, Int> = mapOf(
        "indigo" to R.drawable.bg_card_indigo,
        "blue" to R.drawable.bg_card_blue,
        "teal" to R.drawable.bg_card_teal,
        "green" to R.drawable.bg_card_green,
        "amber" to R.drawable.bg_card_amber,
        "purple" to R.drawable.bg_card_purple,
        "slate" to R.drawable.bg_card_slate,
        "rose" to R.drawable.bg_card_rose
    )

    @DrawableRes
    fun gradientFor(colorKey: String?): Int =
        gradients[colorKey] ?: gradients.getValue("indigo")
}

/** Lightweight item for the wallet card list — never carries a full decrypted number. */
data class DocumentCardUiModel(
    val id: Long,
    val type: DocumentCardType,
    val holderName: String,
    val maskedNumber: String,
    val expiryText: String,
    val issuerText: String,
    val frontImageEncryptedPath: String?,
    val hasBackImage: Boolean,
    val isFavorite: Boolean,
    val colorKey: String
)

/** Full decrypted detail, used only on the detail/edit screens (FLAG_SECURE protected). */
data class DocumentCardDetail(
    val id: Long,
    val type: DocumentCardType,
    val holderName: String,
    val fullNumber: String,
    val secondaryText: String,
    val issuerText: String,
    val notes: String,
    val expiryDate: Long?,
    val frontImageEncryptedPath: String?,
    val backImageEncryptedPath: String?,
    val isFavorite: Boolean,
    val colorKey: String,
    val createdAt: Long,
    val updatedAt: Long
)

/** Everything needed to create or update a card. Images are picked Uris (null = unchanged). */
data class DocumentCardInput(
    val id: Long? = null,
    val type: DocumentCardType,
    val holderName: String,
    val documentNumber: String,
    val secondaryText: String,
    val issuer: String,
    val notes: String,
    val expiryDate: Long?,
    val colorKey: String,
    val frontImageUri: Uri? = null,
    val backImageUri: Uri? = null,
    val removeBackImage: Boolean = false
)

/** Masks a document number for previews, keeping only the last 4 visible: `•••• 4321`. */
object DocumentNumberMasker {
    private const val DOT = '•'

    fun mask(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val visible = trimmed.takeLast(4)
        return if (trimmed.length <= 4) {
            DOT.toString().repeat(trimmed.length)
        } else {
            "${DOT.toString().repeat(4)} $visible"
        }
    }
}
