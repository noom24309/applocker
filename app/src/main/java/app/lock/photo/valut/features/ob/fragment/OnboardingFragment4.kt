package app.lock.photo.valut.features.ob.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.startActivity
import com.google.firebase.remoteconfig.get
import app.lock.photo.valut.R
import app.lock.photo.valut.ad_mob.AdsProvider
import app.lock.photo.valut.ad_mob.isInterstitialAdOnScreen1
import app.lock.photo.valut.ad_mob.util.LoadAdsDialog
import app.lock.photo.valut.databinding.FragmentOnboarding4Binding
import app.lock.photo.valut.features.auth.unlock.ChooseUnlockMethodActivity
import app.lock.photo.valut.features.home.MainActivity
import app.lock.photo.valut.features.ob.OnBoardingActivity
import com.ads.control.ads.VioAdmob
import com.ads.control.ads.VioAdmobCallback
import com.ads.control.ads.wrapper.ApAdError
import com.ads.control.ads.wrapper.ApInterstitialAd
import com.wastickers.romantic.stickers.loveromance.activities.onboarding.BaseFragment

import com.wastickers.romantic.stickers.loveromance.ad_mob.util.showNativeAd
import com.wastickers.romantic.stickers.loveromance.helper.baseConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.jvm.java

class OnboardingFragment4 : BaseFragment() {

    companion object {
        const val EXTRA_REQUEST_NOTIFICATION_PERMISSION = "request_notification_permission"
    }

    lateinit var binding: FragmentOnboarding4Binding
    private val remoteConfig = getRemoteConfig()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOnboarding4Binding.inflate(inflater, container, false)

        // Start loading ad as early as possible
        if (remoteConfig["OB4"].asBoolean()) {
            activity?.let { AdsProvider.nativeOnBoardingThree.loadAds(it) }
        }

        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showNativeOnBoardOne()

        binding.ivChecked.setOnClickListener {
            if (remoteConfig["InterOB"].asBoolean()) {
                LoadAdsDialog.showLoadAdsDialog(activity!!)

                VioAdmob.getInstance()
                    .getInterstitialAds(activity!!, getString(R.string.InterOB), object :
                        VioAdmobCallback() {
                        override fun onInterstitialLoad(interstitialAd: ApInterstitialAd?) {
                            super.onInterstitialLoad(interstitialAd)
                            VioAdmob.getInstance()
                                .forceShowInterstitial(
                                    activity!!,
                                    interstitialAd,
                                    object : VioAdmobCallback() {
                                        override fun onNextAction() {
                                            super.onNextAction()

                                            activity!!.baseConfig.appStarted = true
                                            startActivity(Intent(activity!!, ChooseUnlockMethodActivity::class.java))
                                            activity!!.finish()
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

                                            activity!!.baseConfig.appStarted = true
                                            startActivity(Intent(activity!!, ChooseUnlockMethodActivity::class.java))
                                            activity!!.finish()
                                        }

                                    },
                                    false
                                )
                        }

                        override fun onAdFailedToLoad(adError: ApAdError?) {
                            super.onAdFailedToLoad(adError)
                            LoadAdsDialog.dismissLoadAdsDialog()

                            activity!!.baseConfig.appStarted = true
                            startActivity(Intent(activity!!, ChooseUnlockMethodActivity::class.java))
                            activity!!.finish()
                        }
                    })

            } else {

                activity!!.baseConfig.appStarted = true
                startActivity(Intent(activity!!, ChooseUnlockMethodActivity::class.java))
                activity!!.finish()
            }

        }
    }

    override fun onResume() {
        super.onResume()
        isInterstitialAdOnScreen1 = false

        Log.e("Native_onboarding_3", "onResume: ")
        if (remoteConfig["OB4"].asBoolean()) {
            activity?.let { AdsProvider.nativeOnBoardingThree.loadAds(it) }
            // Re-show after ad reload so freshly loaded ad gets displayed
            nativeOnBoardOnePopulated2.value = false
            showNativeOnBoardOne()
        }
    }

    private var nativeOnBoardOnePopulated2 = MutableStateFlow(false)
    private fun showNativeOnBoardOne() {
        nativeOnBoardOnePopulated2.value = false
        showNativeAd(
            adGroup = AdsProvider.nativeOnBoardingThree,
            frameLayout = binding?.flAdNative,
            adLayout = R.layout.custom_admob_native_layout_1,
            nativeAdPopulatedFlow = nativeOnBoardOnePopulated2,
            facebookAdLayout = R.layout.custom_admob_native_layout_1_white
        )
    }

    private fun openMainScreen(requestNotificationPermission: Boolean) {
        activity!!.baseConfig.appStarted = true
        val intent = Intent(activity!!, MainActivity::class.java).apply {
            putExtra(EXTRA_REQUEST_NOTIFICATION_PERMISSION, requestNotificationPermission)
        }
        startActivity(intent)
        activity!!.finish()
    }
}
