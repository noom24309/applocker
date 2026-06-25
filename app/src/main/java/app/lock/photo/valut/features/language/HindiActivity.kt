package com.wastickers.romantic.stickers.loveromance.ui.language

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import android.window.OnBackInvokedDispatcher
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.os.BuildCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ActivityHindiBinding
import com.wastickers.romantic.stickers.loveromance.BaseClass
import com.wastickers.romantic.stickers.loveromance.helper.baseConfig
import com.wastickers.romantic.stickers.loveromance.ui.language.adapter.LanguagesAdapter
import com.wastickers.romantic.stickers.loveromance.ui.language.data.LanguageDataProvider
import com.wastickers.romantic.stickers.loveromance.ui.language.model.Language
import com.wastickers.romantic.stickers.loveromance.ui.settings.SettingActivity
import app.lock.photo.valut.features.home.MainActivity
import app.lock.photo.valut.features.ob.OnBoardingActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HindiActivity : BaseClass() {

    private lateinit var binding: ActivityHindiBinding
    private lateinit var adapter: LanguagesAdapter
    private var selectedLanguage: Language? = null

    @Inject lateinit var provider: LanguageDataProvider
    private val languages by lazy { provider.hindiVariants() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHindiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideNavigationBar()
        binding.flAdNative.visibility = View.GONE

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                APPEARANCE_LIGHT_STATUS_BARS,
                APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        binding.ivBack.setOnClickListener { finish() }
        setupBackHandling()

        binding.rvLanguages.layoutManager = LinearLayoutManager(this)

        adapter = LanguagesAdapter(languages) { language ->
            selectedLanguage = language
            languages.forEach { it.isChecked = it == language }
            adapter.notifyDataSetChanged()
            binding.ivChecked.visibility = View.VISIBLE
        }
        binding.rvLanguages.adapter = adapter
        binding.rvLanguages.scheduleLayoutAnimation()

        binding.ivChecked.setOnClickListener { onContinue() }
    }

    private fun onContinue() {
        val lang = selectedLanguage ?: run {
            Toast.makeText(this, R.string.select_language_prompt, Toast.LENGTH_SHORT).show()
            return
        }

        baseConfig.selectedLanguage = lang.languageCode
        baseConfig.selectedLanguageName = lang.name

        if (SettingActivity.comeFromLangauge) {
            startActivity(Intent(this@HindiActivity, MainActivity::class.java))
        } else {
            startActivity(Intent(this@HindiActivity, OnBoardingActivity::class.java))
        }
        finish()
    }

    private fun setupBackHandling() {
        if (BuildCompat.isAtLeastT()) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { finish() }
        } else {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = finish()
            })
        }
    }
}
