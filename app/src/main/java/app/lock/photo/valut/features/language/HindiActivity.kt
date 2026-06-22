package com.wastickers.romantic.stickers.loveromance.ui.language

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import android.window.OnBackInvokedDispatcher
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.os.BuildCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.wastickers.romantic.stickers.loveromance.BaseClass
import app.lock.photo.valut.App
import app.lock.photo.valut.R
import com.wastickers.romantic.stickers.loveromance.ads.InterstitialAds
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig.isInterLanguageEnabled
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.showLoadedNativeAd
import app.lock.photo.valut.databinding.ActivityHindiBinding
import com.wastickers.romantic.stickers.loveromance.helper.baseConfig
import com.wastickers.romantic.stickers.loveromance.ui.dialogs.BlockingProgressDialog
import com.wastickers.romantic.stickers.loveromance.ui.language.adapter.LanguagesAdapter
import com.wastickers.romantic.stickers.loveromance.ui.language.data.LanguageDataProvider
import com.wastickers.romantic.stickers.loveromance.ui.language.model.Language
import app.lock.photo.valut.features.home.MainActivity
import app.lock.photo.valut.features.ob.OnBoardingActivity
import com.wastickers.romantic.stickers.loveromance.ui.settings.SettingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlin.collections.forEach
import kotlin.jvm.java

@AndroidEntryPoint
class HindiActivity : BaseClass() {

    private lateinit var binding: ActivityHindiBinding
    private lateinit var adapter: LanguagesAdapter
    private var selectedLanguage: Language? = null

    @Inject lateinit var provider: LanguageDataProvider
    private val languages by lazy { provider.hindiVariants() }
    private val remoteConfig = getRemoteConfig()

    private val nativeLanguagePopulated = MutableStateFlow(false)
    private var nativeLanguageJob: Job? = null
    private var nativeLanguageHindiDupJob: Job? = null
    private lateinit var progress: BlockingProgressDialog
    var isRelpadStarted = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHindiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideNavigationBar()
        progress = BlockingProgressDialog(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            controller?.setSystemBarsAppearance(
                APPEARANCE_LIGHT_STATUS_BARS,
                APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

            showNativeLanguage()


        binding.ivBack.setOnClickListener { finish() }
        setupBackHandling()

        binding.rvLanguages.layoutManager = LinearLayoutManager(this)


        adapter = LanguagesAdapter(languages) { language ->
            selectedLanguage = language
            languages.forEach { it.isChecked = it == language }
            adapter.notifyDataSetChanged()

            if (!hasShownLanguageProgress) {
                hasShownLanguageProgress = true

                binding.ivChecked.visibility = View.INVISIBLE

                    binding.ivChecked.visibility = View.VISIBLE
            } else {
                binding.ivChecked.visibility = View.VISIBLE
            }

            if (!isRelpadStarted) {
                isRelpadStarted = true
                showNativeLanaguageDup()
            }
        }
        binding.rvLanguages.adapter = adapter
        binding.rvLanguages.scheduleLayoutAnimation()

        binding.ivChecked.setOnClickListener { onContinue() }
    }

    private var hasShownLanguageProgress = false
    private fun onContinue() {
        val lang = selectedLanguage ?: run {
            Toast.makeText(this, "Please Select Language", Toast.LENGTH_SHORT).show()
            return
        }

        baseConfig.selectedLanguage = lang.languageCode
        baseConfig.selectedLanguageName = lang.name

        if (SettingActivity.comeFromLangauge) {
            startActivity(Intent(this@HindiActivity, MainActivity::class.java))
            finish()
        } else {
            if(isInterLanguageEnabled()){
                val interAd = InterstitialAds(resources.getString(R.string.Inter_OB))
                interAd.loadAndShowAd(this,true){
                    startActivity(Intent(this@HindiActivity, OnBoardingActivity::class.java))
                    finish()
                }

            }else{
                startActivity(Intent(this@HindiActivity, OnBoardingActivity::class.java))
                finish()
            }
        }
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


    private fun showNativeLanguage() {
        if (RemoteConfig.isNative2ndLanguageHindiEnabled()) {
            App.instance.languageNative2ndAd.observe(this@HindiActivity) { nativeAd ->

                if (nativeAd != null) {
                    Log.d("CheckingNativeAd", "nativeAd is NOT null (observer)")
                    showLoadedNativeAd(
                        this@HindiActivity,
                        binding.flAdNative,
                        R.layout.custom_admob_native_layout_1,
                        nativeAd
                    )
                } else {
                    binding.flAdNative.visibility = View.GONE
                }
            }
        } else {
            binding.flAdNative.visibility = View.GONE

        }


    }


    fun showNativeLanaguageDup() {
        if (RemoteConfig.isNative2ndLanguageDupEnabledDub()) {
            App.instance.languageNative2ndAdDub.observe(this@HindiActivity) { nativeAd ->

                if (nativeAd != null) {
                    Log.d("CheckingNativeAd", "nativeAd is NOT null (observer)")

                    showLoadedNativeAd(
                        this@HindiActivity,
                        binding.flAdNative,
                        R.layout.custom_admob_native_layout_1,
                        nativeAd
                    )
                } else {
                    binding.flAdNative.visibility = View.GONE
                }
            }
        } else {
            binding.flAdNative.visibility = View.GONE

        }


    }
}
