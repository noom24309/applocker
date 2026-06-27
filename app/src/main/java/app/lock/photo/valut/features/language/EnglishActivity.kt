package app.lock.photo.valut.features.language

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat.startActivity
import androidx.core.os.BuildCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.recyclerview.widget.LinearLayoutManager
import app.lock.photo.valut.R
import app.lock.photo.valut.ad_mob.AdsProvider
import app.lock.photo.valut.ad_mob.isInterstitialAdOnScreen1
import app.lock.photo.valut.ad_mob.util.LoadAdsDialog
import app.lock.photo.valut.databinding.ActivityEnglishBinding
import app.lock.photo.valut.features.home.MainActivity
import app.lock.photo.valut.features.ob.OnBoardingActivity
import com.ads.control.ads.VioAdmob
import com.ads.control.ads.VioAdmobCallback
import com.ads.control.ads.wrapper.ApAdError
import com.ads.control.ads.wrapper.ApInterstitialAd
import com.google.firebase.remoteconfig.get
import com.wastickers.romantic.stickers.loveromance.BaseClass
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus
import com.wastickers.romantic.stickers.loveromance.ad_mob.util.showNativeAd
import com.wastickers.romantic.stickers.loveromance.helper.baseConfig
import com.wastickers.romantic.stickers.loveromance.ui.language.adapter.LanguagesAdapter
import com.wastickers.romantic.stickers.loveromance.ui.language.data.LanguageDataProvider
import com.wastickers.romantic.stickers.loveromance.ui.language.model.Language
import com.wastickers.romantic.stickers.loveromance.ui.settings.SettingActivity.Companion.comeFromLangauge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlin.jvm.java

@AndroidEntryPoint
class EnglishActivity : BaseClass() {

    private lateinit var binding: ActivityEnglishBinding
    private lateinit var adapter: LanguagesAdapter
    private var selectedLanguage: Language? = null

    @Inject lateinit var provider: LanguageDataProvider
    private val languages by lazy { provider.englishVariants() }

    private val nativeLanguagePopulated = MutableStateFlow(false)
    private var nativeLanguageJob: Job? = null
    private var nativeLanguageHindiDupJob: Job? = null
    var isRelpadStarted = false
    private val remoteConfig = getRemoteConfig()
    private var hasShownLanguageProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEnglishBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideNavigationBar()
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
        hideNavigationBar()
        if (remoteConfig["english_dup"].asBoolean()) {
            AdsProvider.nativeLanguageEnglishDup.loadAds(this)
        }
        if (remoteConfig["nativeEnglish"].asBoolean()) {

            showNativeLanguage()
        }


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

                Handler(Looper.getMainLooper()).postDelayed({
                    binding.ivChecked.visibility = View.VISIBLE
                }, 1000)
            } else {
                binding.ivChecked.visibility = View.VISIBLE
            }

            if (!isRelpadStarted) {
                isRelpadStarted = true
                showNativeLanguageDup()
            }
        }
        binding.rvLanguages.adapter = adapter
        binding.rvLanguages.scheduleLayoutAnimation()

        binding.ivChecked.setOnClickListener { onContinue() }
    }

    private fun onContinue() {
        val lang = selectedLanguage ?: run {
            Toast.makeText(this, "Please Select Language", Toast.LENGTH_SHORT).show()
            return
        }

        baseConfig.selectedLanguage = lang.languageCode
        baseConfig.selectedLanguageName = lang.name

        if (comeFromLangauge) {
            navigateToMainWithSettingsInterstitial()
        } else {
            if (remoteConfig["interApply"].asBoolean()) {
                LoadAdsDialog.showLoadAdsDialog(this)

                VioAdmob.getInstance()
                    .getInterstitialAds(this, getString(R.string.InterLanaguge), object :
                        VioAdmobCallback() {
                        override fun onInterstitialLoad(interstitialAd: ApInterstitialAd?) {
                            super.onInterstitialLoad(interstitialAd)
                            VioAdmob.getInstance()
                                .forceShowInterstitial(
                                    this@EnglishActivity,
                                    interstitialAd,
                                    object : VioAdmobCallback() {
                                        override fun onNextAction() {
                                            super.onNextAction()
                                            startActivity(Intent(this@EnglishActivity, OnBoardingActivity::class.java))
                                            finish()
                                        }

                                        override fun onAdClosed() {
                                            super.onAdClosed()
                                            isInterstitialAdOnScreen1 = false
                                            LoadAdsDialog.dismissLoadAdsDialog()

                                        }

                                        override fun onInterstitialShow() {
                                            super.onInterstitialShow()
                                            isInterstitialAdOnScreen1 = true
                                        }

                                        override fun onAdFailedToShow(adError: ApAdError?) {
                                            super.onAdFailedToShow(adError)
                                            LoadAdsDialog.dismissLoadAdsDialog()

                                            startActivity(Intent(this@EnglishActivity, OnBoardingActivity::class.java))
                                            finish()
                                        }

                                    },
                                    false
                                )
                        }

                        override fun onAdFailedToLoad(adError: ApAdError?) {
                            super.onAdFailedToLoad(adError)
                            LoadAdsDialog.dismissLoadAdsDialog()

                            startActivity(Intent(this@EnglishActivity, OnBoardingActivity::class.java))
                            finish()
                        }
                    })

            } else {

                startActivity(Intent(this@EnglishActivity, OnBoardingActivity::class.java))
                finish()
            }
        }
    }

    private fun navigateToMainWithSettingsInterstitial() {
        if (!remoteConfig["InterLanagugeSettings"].asBoolean()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        LoadAdsDialog.showLoadAdsDialog(this)
        VioAdmob.getInstance().getInterstitialAds(
            this,
            getString(R.string.InterLanaguge),
            object : VioAdmobCallback() {
                override fun onInterstitialLoad(interstitialAd: ApInterstitialAd?) {
                    super.onInterstitialLoad(interstitialAd)
                    VioAdmob.getInstance().forceShowInterstitial(
                        this@EnglishActivity,
                        interstitialAd,
                        object : VioAdmobCallback() {
                            override fun onNextAction() {
                                super.onNextAction()
                                LoadAdsDialog.dismissLoadAdsDialog()
                                startActivity(Intent(this@EnglishActivity, MainActivity::class.java))
                                finish()
                            }

                            override fun onAdClosed() {
                                super.onAdClosed()
                                isInterstitialAdOnScreen1 = false
                            }

                            override fun onInterstitialShow() {
                                super.onInterstitialShow()
                                isInterstitialAdOnScreen1 = true
                            }
                        },
                        false
                    )
                }

                override fun onAdFailedToLoad(adError: ApAdError?) {
                    super.onAdFailedToLoad(adError)
                    LoadAdsDialog.dismissLoadAdsDialog()
                    startActivity(Intent(this@EnglishActivity, MainActivity::class.java))
                    finish()
                }
            }
        )
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
        nativeLanguagePopulated.value = false
        nativeLanguageJob = showNativeAd(
            adGroup = AdsProvider.nativeLanguageEnglish,
            frameLayout = binding.flAdNative,
            adLayout = R.layout.custom_admob_native_layout_1_new,
            nativeAdPopulatedFlow = nativeLanguagePopulated,
            facebookAdLayout = R.layout.custom_admob_native_layout_1,
            keepAdsWhenLoading = true
        )
    }


    override fun onResume() {
        super.onResume()
        if (!isRelpadStarted) AdsProvider.nativeLanguageEnglish.loadAds(this)
        else if (remoteConfig["english_dup"].asBoolean()) AdsProvider.nativeLanguageEnglishDup.loadAds(
            this
        )
        nativeLanguagePopulated.value = false

    }

    private fun showNativeLanguageDup() {
        if(  AdsProvider.nativeLanguageEnglishDup.status== AdStatus.Ready){
            nativeLanguageJob?.cancel()
            nativeLanguagePopulated.value = false

            nativeLanguageHindiDupJob = showNativeAd(
                adGroup = AdsProvider.nativeLanguageEnglishDup,
                frameLayout = binding.flAdNative,
                adLayout = R.layout.custom_admob_native_layout_1,
                nativeAdPopulatedFlow = nativeLanguagePopulated,
                facebookAdLayout = R.layout.custom_admob_native_layout_1,
            )
        }

    }
}
