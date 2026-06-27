package app.lock.photo.valut.features.ob.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.lock.photo.valut.R
import app.lock.photo.valut.ad_mob.AdsProvider
import app.lock.photo.valut.ad_mob.isInterstitialAdOnScreen1
import app.lock.photo.valut.databinding.FragmentOnboarding3Binding
import app.lock.photo.valut.features.ob.OnBoardingActivity
import com.google.firebase.remoteconfig.get
import com.wastickers.romantic.stickers.loveromance.activities.onboarding.BaseFragment
import com.wastickers.romantic.stickers.loveromance.ad_mob.util.showNativeAd
import kotlinx.coroutines.flow.MutableStateFlow


class OnboardingFragment3 : BaseFragment() {

    private lateinit var binding: FragmentOnboarding3Binding
    private val remoteConfig=getRemoteConfig()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentOnboarding3Binding.inflate(inflater, container, false)

        showNativeOnBoardOne()



        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.ivChecked.setOnClickListener {
            val current = (activity as? OnBoardingActivity)?.getCurrentPage() ?: return@setOnClickListener
            (activity as? OnBoardingActivity)?.goToNextPage(current + 1)
        }


    }


    private var isLogged = false
    override fun onResume() {
        super.onResume()
        isInterstitialAdOnScreen1=false
        if(remoteConfig["OB3"].asBoolean()) {
            activity?.let { AdsProvider.nativeOnBoardingTwo.loadAds(it) }
            nativeOnBoardOnePopulated1.value =false
            showNativeOnBoardOne()
        }
    }
    private var nativeOnBoardOnePopulated1 = MutableStateFlow(false)
    private fun showNativeOnBoardOne(){
        nativeOnBoardOnePopulated1.value =false
        showNativeAd(adGroup = AdsProvider.nativeOnBoardingTwo,
            frameLayout = binding?.flAdNative,
            adLayout = R.layout.custom_admob_native_layout_1,
            nativeAdPopulatedFlow = nativeOnBoardOnePopulated1,
            facebookAdLayout = R.layout.custom_admob_native_layout_1_white
        )
    }
}
