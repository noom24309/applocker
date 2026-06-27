package app.lock.photo.valut.features.language

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
import app.lock.photo.valut.ad_mob.AdsProvider
import app.lock.photo.valut.ad_mob.isInterstitialAdOnScreen1
import app.lock.photo.valut.ad_mob.util.LoadAdsDialog
import app.lock.photo.valut.databinding.ActivityLanguage2Binding
import app.lock.photo.valut.features.home.MainActivity
import app.lock.photo.valut.features.ob.OnBoardingActivity
import com.ads.control.ads.VioAdmob
import com.ads.control.ads.VioAdmobCallback
import com.ads.control.ads.wrapper.ApAdError
import com.ads.control.ads.wrapper.ApInterstitialAd
import com.google.firebase.remoteconfig.get
import com.wastickers.romantic.stickers.loveromance.BaseClass
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

@AndroidEntryPoint
class LanguageActivity : BaseClass() {

    private lateinit var binding: ActivityLanguage2Binding
    private lateinit var adapter: LanguagesAdapter
    private var selectedLanguage: Language? = null

    @Inject
    lateinit var provider: LanguageDataProvider

    private val remoteConfig = getRemoteConfig()
    private val languages by lazy { provider.mainLanguages() }

    private var selectionHandler: Handler? = null
    private var selectionRunnable: Runnable? = null

    var isRelpadStarted = false
    private var nativeLanguageJob: Job? = null
    private var nativeLanguageDupJob: Job? = null
    private val nativeLanguagePopulated = MutableStateFlow(false)

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setLocale(baseConfig.selectedLanguage ?: "en")

        binding = ActivityLanguage2Binding.inflate(layoutInflater)
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

        if (remoteConfig["langauge_dup"].asBoolean()) {
            AdsProvider.nativeLanguageDup.loadAds(this)
        }

        if (remoteConfig["nativeLang"].asBoolean()) {
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

            binding.ivChecked.visibility = View.INVISIBLE

            // After 3 seconds: hide progress, show tick
            selectionHandler = Handler(Looper.getMainLooper())
            selectionRunnable = Runnable {
                binding.ivChecked.visibility = View.VISIBLE
            }
            selectionHandler!!.postDelayed(selectionRunnable!!, 1000)

            if (!isRelpadStarted) {
                isRelpadStarted = true
                showNativeLanguageDup()
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
                "hi" ->{
                    if(remoteConfig["nativeHindi"].asBoolean()){

                        AdsProvider.nativeLanguageHindi.loadAds(this)
                    }
                    startActivity(Intent(this, HindiActivity::class.java))
                }

                "en" ->{
                    if(remoteConfig["nativeEnglish"].asBoolean()){

                        AdsProvider.nativeLanguageEnglish.loadAds(this)
                    }
                    startActivity(Intent(this, EnglishActivity::class.java))

                }

                else -> {
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
                                                this@LanguageActivity,
                                                interstitialAd,
                                                object : VioAdmobCallback() {
                                                    override fun onNextAction() {
                                                        super.onNextAction()
                                                        startActivity(Intent(this@LanguageActivity, OnBoardingActivity::class.java))
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

                                                        startActivity(Intent(this@LanguageActivity, OnBoardingActivity::class.java))
                                                        finish()
                                                    }

                                                },
                                                false
                                            )
                                    }

                                    override fun onAdFailedToLoad(adError: ApAdError?) {
                                        super.onAdFailedToLoad(adError)
                                        LoadAdsDialog.dismissLoadAdsDialog()

                                        startActivity(Intent(this@LanguageActivity, OnBoardingActivity::class.java))
                                        finish()
                                    }
                                })

                        } else {

                            startActivity(Intent(this@LanguageActivity, OnBoardingActivity::class.java))
                            finish()
                        }




                    }
                }
            }
        }
    }

    private fun showNativeLanguage() {
        nativeLanguagePopulated.value = false
        nativeLanguageJob = showNativeAd(
            adGroup = AdsProvider.nativeLanguage,
            frameLayout = binding.flAdNative,
            adLayout = R.layout.custom_admob_native_layout_1,
            nativeAdPopulatedFlow = nativeLanguagePopulated,
            facebookAdLayout = R.layout.custom_admob_native_layout_1_new,
            keepAdsWhenLoading = true
        )
    }

    private fun showNativeLanguageDup() {
        nativeLanguageJob?.cancel()
        nativeLanguagePopulated.value = false
        nativeLanguageDupJob = showNativeAd(
            adGroup = AdsProvider.nativeLanguageDup,
            frameLayout = binding.flAdNative,
            adLayout = R.layout.custom_admob_native_layout_1,
            nativeAdPopulatedFlow = nativeLanguagePopulated,
            facebookAdLayout = R.layout.custom_admob_native_layout_1_new
        )
    }

    override fun onResume() {
        super.onResume()

        if (!isRelpadStarted) {
            if(remoteConfig["nativeLang"].asBoolean()){
                AdsProvider.nativeLanguage.loadAds(this)

            }
        } else if (remoteConfig["langauge_dup"].asBoolean()) {
            AdsProvider.nativeLanguageDup.loadAds(this)
        }

        nativeLanguagePopulated.value = false
    }

    override fun onDestroy() {
        nativeLanguageJob?.cancel()
        nativeLanguageDupJob?.cancel()
        selectionRunnable?.let { selectionHandler?.removeCallbacks(it) }
        super.onDestroy()
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
                        this@LanguageActivity,
                        interstitialAd,
                        object : VioAdmobCallback() {
                            override fun onNextAction() {
                                super.onNextAction()
                                LoadAdsDialog.dismissLoadAdsDialog()
                                startActivity(Intent(this@LanguageActivity, MainActivity::class.java))
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
                    startActivity(Intent(this@LanguageActivity, MainActivity::class.java))
                    finish()
                }
            }
        )
    }

}
