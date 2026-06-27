package app.lock.photo.valut.features.ob.fragment

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.lock.photo.valut.R
import app.lock.photo.valut.ad_mob.AdsProvider
import app.lock.photo.valut.ad_mob.isInterstitialAdOnScreen1
import app.lock.photo.valut.databinding.FragmentObScreen4Binding
import app.lock.photo.valut.features.ob.OnBoardingActivity
import com.google.firebase.remoteconfig.get
import app.lock.photo.valut.features.ob.OnboardingSwipeLockController
import com.wastickers.romantic.stickers.loveromance.activities.onboarding.BaseFragment
import com.wastickers.romantic.stickers.loveromance.ad_mob.enums.AdStatus
import com.wastickers.romantic.stickers.loveromance.ad_mob.util.showNativeAd
import com.wastickers.romantic.stickers.loveromance.helper.beVisible
import kotlinx.coroutines.flow.MutableStateFlow

open class NativeFullSrcOBFragment2 : BaseFragment() {

    private lateinit var binding: FragmentObScreen4Binding
    private var isTimerCompleted = false
    private var swipeLockController: OnboardingSwipeLockController? = null
    private val remoteConfig=getRemoteConfig()
    private var nativeFullScreenPopulated = MutableStateFlow(false)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentObScreen4Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showNativeFullScreen()
        binding.btnNextStep.setOnClickListener { goToOffset(1) }
        binding.btnClose.setOnClickListener {
            (activity!! as OnBoardingActivity).goNext()

        }
        binding.btnPreStep.setOnClickListener { goToOffset(-1) }
    }


    override fun onResume() {
        super.onResume()
        isInterstitialAdOnScreen1 =true
        if (remoteConfig["nativefullScreen"].asBoolean()) {
            activity?.let { AdsProvider.nativeFulScreen2.loadAds(it) }
            nativeFullScreenPopulated.value = false
        }

    }

    override fun onPause() {
        if (::binding.isInitialized) {
        }
        super.onPause()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        swipeLockController = context as? OnboardingSwipeLockController
    }

    override fun onDetach() {
        swipeLockController = null
        super.onDetach()
    }

    private fun goToOffset(offset: Int) {
        if (!isTimerCompleted) return
        val host = activity as? OnBoardingActivity ?: return
        host.goToNextPage(host.getCurrentPage() + offset)
    }

    private fun showNativeFullScreen() {
        val activity = activity as? OnBoardingActivity
        if (activity == null) {
            Log.e("TAG", "Activity is null or not HowToUseActivity")
            return
        }
        if(AdsProvider.nativeFulScreen2.status== AdStatus.Failure){
            goToOffset(1)
            return
        }
        nativeFullScreenPopulated.value = false
        Log.e("native_fullscreen", "showNativeFullScreen111111111: "+ AdsProvider.nativeFulScreen2.status )
        if (remoteConfig["nativefullScreen"].asBoolean()) {
            if (AdsProvider.nativeFulScreen2.status == AdStatus.Failure) {
                binding.clEmpty.beVisible()
                Log.e("native_fullscreen", "showNativeFullScreen: AD Failed", )
            } else {
                showNativeAd(
                    adGroup = AdsProvider.nativeFulScreen2,
                    frameLayout = binding.flAdNative, R.layout.custom_native_full_screen,
                    nativeAdPopulatedFlow = nativeFullScreenPopulated,
                    facebookAdLayout = R.layout.custom_native_full_screen
                )
            }
        } else {
            Log.e("native_fullscreen", "showNativeFullScreen: AD Failed to load", )

        }

    }
}