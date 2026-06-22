package com.wastickers.romantic.stickers.loveromance.ui.language.model

/**
 * A selectable language. [name] is the native-script label shown as the primary text
 * (e.g. "हिन्दी", "العربية"); [englishName] is the English subtitle. No flag is used.
 */
data class Language(
    val name: String,
    val englishName: String,
    val languageCode: String,
    var isChecked: Boolean
)
