package com.wastickers.romantic.stickers.loveromance.ui.language

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ActivityLanguage2Binding
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
class LanguageActivity : BaseClass() {

    private lateinit var binding: ActivityLanguage2Binding
    private lateinit var adapter: LanguagesAdapter
    private var selectedLanguage: Language? = null

    @Inject
    lateinit var provider: LanguageDataProvider

    private val languages by lazy { provider.mainLanguages() }

    private var selectionHandler: Handler? = null
    private var selectionRunnable: Runnable? = null

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setLocale(baseConfig.selectedLanguage ?: "en")

        binding = ActivityLanguage2Binding.inflate(layoutInflater)
        setContentView(binding.root)
        hideNavigationBar()
        binding.flAdNative.visibility = View.GONE

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.main.setPadding(bars.left, bars.top, bars.right, bars.bottom)
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

        setupRecycler()
        setupContinueClick()
    }

    private fun setupRecycler() {
        binding.rvLanguages.layoutManager = LinearLayoutManager(this)

        adapter = LanguagesAdapter(languages) { language ->
            selectedLanguage = language
            languages.forEach { it.isChecked = it == language }
            adapter.notifyDataSetChanged()

            selectionRunnable?.let { selectionHandler?.removeCallbacks(it) }
            binding.ivChecked.visibility = View.INVISIBLE
            selectionHandler = Handler(Looper.getMainLooper())
            selectionRunnable = Runnable { binding.ivChecked.visibility = View.VISIBLE }
            selectionHandler!!.postDelayed(selectionRunnable!!, 100)
        }

        binding.rvLanguages.adapter = adapter
        binding.rvLanguages.scheduleLayoutAnimation()
    }

    private fun setupContinueClick() {
        binding.ivChecked.setOnClickListener {
            val lang = selectedLanguage ?: run {
                Toast.makeText(this, R.string.select_language_prompt, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            baseConfig.selectedLanguage = lang.languageCode
            baseConfig.selectedLanguageName = lang.name

            when (lang.languageCode) {
                "hi" -> startActivity(Intent(this, HindiActivity::class.java))
                "en" -> startActivity(Intent(this, EnglishActivity::class.java))
                else -> {
                    if (SettingActivity.comeFromLangauge) {
                        startActivity(Intent(this, MainActivity::class.java))
                    } else {
                        startActivity(Intent(this, OnBoardingActivity::class.java))
                    }
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        selectionRunnable?.let { selectionHandler?.removeCallbacks(it) }
        super.onDestroy()
    }
}
