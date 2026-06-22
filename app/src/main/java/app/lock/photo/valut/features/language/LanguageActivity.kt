package com.wastickers.romantic.stickers.loveromance.ui.language

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ActivityLanguage2Binding
import com.wastickers.romantic.stickers.loveromance.ui.language.adapter.LanguagesAdapter
import com.wastickers.romantic.stickers.loveromance.ui.language.data.LanguageDataProvider
import com.wastickers.romantic.stickers.loveromance.ui.language.model.Language
import dagger.hilt.android.AndroidEntryPoint
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.wastickers.romantic.stickers.loveromance.BaseClass
import app.lock.photo.valut.App
import app.lock.photo.valut.App.Companion.canRequestAd
import com.wastickers.romantic.stickers.loveromance.ads.InterstitialAds
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig.isInterLanguageEnabled
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.loadAndReturnAd
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.showLoadedNativeAd
import com.wastickers.romantic.stickers.loveromance.helper.baseConfig
import com.wastickers.romantic.stickers.loveromance.helper.beGone
import com.wastickers.romantic.stickers.loveromance.ui.dialogs.BlockingProgressDialog
import app.lock.photo.valut.features.home.MainActivity
import app.lock.photo.valut.features.ob.OnBoardingActivity
import com.wastickers.romantic.stickers.loveromance.ui.settings.SettingActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlin.jvm.java

@AndroidEntryPoint
class LanguageActivity : BaseClass() {

    private lateinit var binding: ActivityLanguage2Binding
    private lateinit var adapter: LanguagesAdapter
    private var selectedLanguage: Language? = null

    @Inject
    lateinit var provider: LanguageDataProvider

    private val remoteConfig = getRemoteConfig()
    private lateinit var progress: BlockingProgressDialog
    private val languages by lazy { provider.mainLanguages() }

    private var loadingAnimator: ValueAnimator? = null
    private var selectionHandler: Handler? = null
    private var selectionRunnable: Runnable? = null

    var isRelpadStarted = false
    private var nativeLanguageJob: Job? = null
    private var nativeLanguageDupJob: Job? = null
    private val nativeLanguagePopulated = MutableStateFlow(false)


    @RequiresApi(Build.VERSION_CODES.N_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setLocale( baseConfig.selectedLanguage ?: "en")

        binding = ActivityLanguage2Binding.inflate(layoutInflater)
        setContentView(binding.root)
        hideNavigationBar()

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.main.setPadding(
                bars.left,
                bars.top,
                bars.right,
                bars.bottom
            )

            insets
        }

        progress = BlockingProgressDialog(this)

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

        if (RemoteConfig.isNativeLanguageDubEnabled() && canRequestAd) {
            loadAndReturnAd(
                this@LanguageActivity,
                resources.getString(R.string.native_language_dup),
                adResult = {
                    App.instance.languageNativeAdDub.value = it
                })
        }

        if (RemoteConfig.isNativeLanguageEnabled() && canRequestAd) {
            showNativeLanguage()
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

            // Cancel any pending selection timer
            selectionRunnable?.let { selectionHandler?.removeCallbacks(it) }

            // Show progress, hide tick
            binding.ivChecked.visibility = View.INVISIBLE

            // After 3 seconds: hide progress, show tick
            selectionHandler = Handler(Looper.getMainLooper())
            selectionRunnable = Runnable {
                binding.ivChecked.visibility = View.VISIBLE
            }
            selectionHandler!!.postDelayed(selectionRunnable!!, 100)

            if (!isRelpadStarted) {
                isRelpadStarted = true
                showNativeLanaguageDup()
            }
        }

        binding.rvLanguages.adapter = adapter
        binding.rvLanguages.scheduleLayoutAnimation()
    }

    private fun setupContinueClick() {
        binding.ivChecked.setOnClickListener {

            val lang = selectedLanguage ?: run {
                Toast.makeText(this, "Please Select Language", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            baseConfig.selectedLanguage = lang.languageCode
            baseConfig.selectedLanguageName = lang.name

            when (lang.languageCode) {
                "hi" ->
                {
                    loadnativeHindi()
                    loadnativeHindiDup()
                    startActivity(Intent(this, HindiActivity::class.java))

                }
                "en" ->
                {
                    loadnativeEnglish()
                    loadnativeEnglishDup()
                    startActivity(Intent(this, EnglishActivity::class.java))

                }
                else -> {
                    if (SettingActivity.comeFromLangauge) {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        if(isInterLanguageEnabled()){
                            val interAd = InterstitialAds(resources.getString(R.string.Inter_OB))
                            interAd.loadAndShowAd(this,true){
                                startActivity(Intent(this@LanguageActivity, OnBoardingActivity::class.java))
                                finish()
                            }

                        }else{
                            startActivity(Intent(this@LanguageActivity, OnBoardingActivity::class.java))
                            finish()
                        }

                    }
                }
            }
        }
    }


    private fun showNativeLanguage() {
        App.instance.languageNativeAd.observe(this@LanguageActivity) { nativeAd ->
            Log.d("CheckingNativeAd", "nativeAd is  null (observer)")

            if (nativeAd != null) {
                Log.d("CheckingNativeAd", "nativeAd is NOT null (observer)")

                showLoadedNativeAd(
                    this@LanguageActivity,
                    binding.flAdNative,
                    R.layout.custom_admob_native_layout_1,
                    nativeAd
                )
            } else {
                binding.flAdNative.visibility = View.GONE
            }
        }

    }
    fun showNativeLanaguageDup() {
        if (RemoteConfig.isNativeLanguageDubEnabled()) {
            App.instance.languageNativeAdDub.observe(this@LanguageActivity) { nativeAd ->

                if (nativeAd != null) {
                    Log.d("CheckingNativeAd", "nativeAd is NOT null (observer)")

                    showLoadedNativeAd(
                        this@LanguageActivity,
                        binding.flAdNative,
                        R.layout.custom_admob_native_layout_1,
                        nativeAd
                    )
                } else {
                    binding.flAdNative.visibility = View.GONE
                }
            }
        } else {
            binding.flAdNative.beGone()
        }
    }








    fun loadnativeEnglish() {
        if (RemoteConfig.isNative2ndLanguageEnabled() && canRequestAd) {
            loadAndReturnAd(
                this@LanguageActivity,
                resources.getString(R.string.native_language_other),
                adResult = {
                    App.instance.languageNative2ndAd.value = it
                })
        }
    }

    fun loadnativeEnglishDup() {
        if (RemoteConfig.isNative2ndLanguageEnabledDub() && canRequestAd) {
            loadAndReturnAd(
                this@LanguageActivity,
                resources.getString(R.string.native_language_otherDup),
                adResult = {
                    App.instance.languageNative2ndAdDub.value = it
                })
        }
    }


    fun loadnativeHindi() {
        if (RemoteConfig.isNative2ndLanguageHindiEnabled() && canRequestAd) {
            loadAndReturnAd(
                this@LanguageActivity,
                resources.getString(R.string.native_language_other),
                adResult = {
                    App.instance.languageNative2ndAd.value = it
                })
        }
    }

    fun loadnativeHindiDup() {
        if (RemoteConfig.isNative2ndLanguageDupEnabledDub() && canRequestAd) {
            loadAndReturnAd(
                this@LanguageActivity,
                resources.getString(R.string.native_language_otherDup),
                adResult = {
                    App.instance.languageNative2ndAdDub.value = it
                })
        }
    }


    override fun onResume() {
        super.onResume()

    }

    override fun onDestroy() {
        loadingAnimator?.cancel()
        nativeLanguageJob?.cancel()
        nativeLanguageDupJob?.cancel()
        selectionRunnable?.let { selectionHandler?.removeCallbacks(it) }
        super.onDestroy()
    }
}
