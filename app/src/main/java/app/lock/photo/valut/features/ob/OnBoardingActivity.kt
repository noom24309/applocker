package app.lock.photo.valut.features.ob

import android.os.Bundle
import androidx.viewpager2.widget.ViewPager2
import com.google.firebase.remoteconfig.get
import com.wastickers.romantic.stickers.loveromance.BaseClass
import app.lock.photo.valut.App
import app.lock.photo.valut.App.Companion.canRequestAd
import app.lock.photo.valut.R
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.loadAndReturnAd
import app.lock.photo.valut.databinding.ActivityOnboardingBinding
import com.wastickers.romantic.stickers.loveromance.helper.baseConfig
import app.lock.photo.valut.features.ob.adapter.OnBoardingPagerAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnBoardingActivity : BaseClass(){

    var binding: ActivityOnboardingBinding? = null

    private lateinit var adapter: OnBoardingPagerAdapter  // make it a field
    private val remoteConfig = getRemoteConfig()
    private var isAdLoading = false
    var s = 0f
    var isSwipeRight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setLocale(baseConfig.selectedLanguage!!)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding?.root)
hideNavigationBar()
        gotoFullScreenView()
        adapter = OnBoardingPagerAdapter(this)
        binding?.viewPager?.adapter = adapter
        binding?.viewPager?.offscreenPageLimit = 1

        // Load ads AFTER adapter is set — adapter updates itself when results arrive
        if (remoteConfig["nativefullScreen"].asBoolean() && canRequestAd) {
        }
        loadTutorialNativeAdsFullScreen()
        if (remoteConfig["nativefullScreen2"].asBoolean() && canRequestAd) {
        }
        loadTutorialNativeAdsFullScreen2()

        binding?.viewPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                isSwipeRight = position + positionOffset > s
                s = position + positionOffset
            }
            override fun onPageSelected(position: Int) {}
        })
    }

    private fun loadTutorialNativeAdsFullScreen() {
        if (RemoteConfig.isNativeFullScreen1Enabled() && canRequestAd) {
            loadAndReturnAd(
                this,
                resources.getString(R.string.native_obfull),
                adResult = { ad ->
                    App.instance.nativeFullScreen1.value = ad
                    // Only show the fragment if ad loaded successfully (non-null)
                    adapter.showNativeFull1 = ad != null
                }
            )
        }
        // remote config false OR canRequestAd false → showNativeFull1 stays false → fragment excluded
    }

    private fun loadTutorialNativeAdsFullScreen2() {
        if (RemoteConfig.isNativeFullScreen2Enabled() && canRequestAd) {
            loadAndReturnAd(
                this,
                resources.getString(R.string.native_obfull2),
                adResult = { ad ->
                    App.instance.nativeFullScreen2.value = ad
                    // Only show the fragment if ad loaded successfully (non-null)
                    adapter.showNativeFull2 = ad != null
                }
            )
        }
    }

    fun goToNextPage(position: Int) {
        if (position < (binding?.viewPager?.adapter?.itemCount ?: 0)) {
            binding?.viewPager?.currentItem = position
        }
    }

    fun getCurrentPage(): Int = binding?.viewPager?.currentItem!!

    override fun onDestroy() {
        super.onDestroy()
        isAdLoading = false
    }


}