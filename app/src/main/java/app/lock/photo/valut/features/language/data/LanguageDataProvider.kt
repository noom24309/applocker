package com.wastickers.romantic.stickers.loveromance.ui.language.data

import com.wastickers.romantic.stickers.loveromance.ui.language.model.Language
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguageDataProvider @Inject constructor() {

    // Primary = native-script name, subtitle = English name. No flags.
    fun mainLanguages(): MutableList<Language> = mutableListOf(
        Language("English", "Default", "en", false),
        Language("हिन्दी", "Hindi", "hi", false),
        Language("Español", "Spanish", "es", false),
        Language("العربية", "Arabic", "ar", false),
        Language("বাংলা", "Bengali", "bn", false),
        Language("Português", "Portuguese", "pt", false),
        Language("Bahasa Indonesia", "Indonesian", "in", false),
        Language("Türkçe", "Turkish", "tr", false),
        Language("中文(简体)", "Chinese (Simplified)", "zh", false),
        Language("日本語", "Japanese", "ja", false),
        Language("Русский", "Russian", "ru", false),
        Language("Deutsch", "German", "de", false),
        Language("Nederlands", "Dutch", "nl", false),
    )

    fun englishVariants(): MutableList<Language> = mutableListOf(
        Language("English (US)", "United States", "en", false),
        Language("English (UK)", "United Kingdom", "en-gb", false),
        Language("English (India)", "India", "en", false),
        Language("English (Australia)", "Australia", "en", false),
        Language("English (Singapore)", "Singapore", "en", false),
        Language("English (New Zealand)", "New Zealand", "en", false),
        Language("English (South Africa)", "South Africa", "en", false),
    )

    fun hindiVariants(): MutableList<Language> = mutableListOf(
        Language("हिन्दी", "Hindi", "hi", false),
        Language("اردو", "Urdu", "ur", false),
        Language("தமிழ்", "Tamil", "ta", false),
        Language("मराठी", "Marathi", "mr", false),
        Language("ಕನ್ನಡ", "Kannada", "kn", false),
        Language("తెలుగు", "Telugu", "te", false),
        Language("বাংলা", "Bengali", "bn", false),
        Language("ગુજરાતી", "Gujarati", "gu", false),
        Language("ଓଡ଼ିଆ", "Odia", "or", false),
        Language("മലയാളം", "Malayalam", "ml", false),
        Language("ਪੰਜਾਬੀ", "Punjabi", "pa", false),
        Language("অসমীয়া", "Assamese", "as", false),
    )
}
