package app.lock.photo.valut.features.ob.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.lock.photo.valut.R
import app.lock.photo.valut.ad_mob.AdsProvider
import app.lock.photo.valut.databinding.FragmentOnboarding1Binding
import app.lock.photo.valut.features.ob.OnBoardingActivity
import com.google.firebase.remoteconfig.get
import com.wastickers.romantic.stickers.loveromance.activities.onboarding.BaseFragment
import com.wastickers.romantic.stickers.loveromance.ad_mob.util.showNativeAd
import kotlinx.coroutines.flow.MutableStateFlow

class OnboardingFragment1 : BaseFragment() {

    private lateinit var binding: FragmentOnboarding1Binding

    private val remoteConfig=getRemoteConfig()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentOnboarding1Binding.inflate(inflater, container, false)

        if(remoteConfig["OB1"].asBoolean()){
            activity?.let { AdsProvider.nativeOnBoarding.loadAds(it) }
            nativeOnBoardOnePopulated.value = false
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ivChecked.setOnClickListener {
            val current = (activity as? OnBoardingActivity)?.getCurrentPage() ?: return@setOnClickListener
            (activity as? OnBoardingActivity)?.goToNextPage(current + 1)
        }


        Log.e("Native_onboarding", "onViewCreated: " + remoteConfig["nativeOnboard2"].asBoolean())



        if (remoteConfig["OB2"].asBoolean()) {
            activity?.let { AdsProvider.nativeOnBoarding1.loadAds(it) }
        }
        if (remoteConfig["OB1"].asBoolean()) {

            showNativeOnBoardOne()
        }


    }

    private var isLogged = false
    override fun onResume() {
        super.onResume()
        if(remoteConfig["OB1"].asBoolean()){
            activity?.let { AdsProvider.nativeOnBoarding.loadAds(it) }
            nativeOnBoardOnePopulated.value = false
        }

    }

    private var nativeOnBoardOnePopulated = MutableStateFlow(false)
    private fun showNativeOnBoardOne() {
        nativeOnBoardOnePopulated.value = false
        showNativeAd(
            adGroup = AdsProvider.nativeOnBoarding,
            frameLayout = binding?.flAdNative,
            adLayout = R.layout.custom_admob_native_layout_1,
            nativeAdPopulatedFlow = nativeOnBoardOnePopulated,
            facebookAdLayout = R.layout.custom_admob_native_layout_1_white
        )
    }


}