package app.lock.photo.valut.features.ob

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.viewpager2.widget.ViewPager2
import app.lock.photo.valut.ad_mob.AdsProvider
import app.lock.photo.valut.databinding.ActivityOnboardingBinding
import app.lock.photo.valut.features.home.MainActivity
import com.google.firebase.remoteconfig.get
import com.wastickers.romantic.stickers.loveromance.BaseClass
import com.wastickers.romantic.stickers.loveromance.helper.baseConfig
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OnBoardingActivity : BaseClass() {

    private var _binding: ActivityOnboardingBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var pagesProvider: OnboardingPagesProvider

    private val remoteConfig = getRemoteConfig()
    private lateinit var pagerAdapter: PagerAdapter
    private var pageCallback: ViewPager2.OnPageChangeCallback? = null
    private var isOnboardingNavigationEnabled = true
    private var isTimerNavigationLocked = false
    private var timerLockedPage = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideNavigationBar()
        gotoFullScreenView()
        loadInterstitial()

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

        if (remoteConfig["nativefullScreen"].asBoolean()) {
            AdsProvider.nativeFulScreen.loadAds(this)
        }
        if (remoteConfig["nativefullScreen2"].asBoolean()) {
            AdsProvider.nativeFulScreen2.loadAds(this)
        }


        pagerAdapter = PagerAdapter(this, pagesProvider)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 1

        // Only keep callback if you actually need page events
        pageCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (isTimerNavigationLocked && position != timerLockedPage) {
                    binding.viewPager.post {
                        if (isTimerNavigationLocked && timerLockedPage != -1) {
                            binding.viewPager.setCurrentItem(timerLockedPage, false)
                        }
                    }
                }
            }
        }
        binding.viewPager.registerOnPageChangeCallback(pageCallback!!)
    }

    override fun onDestroy() {
        pageCallback?.let { binding.viewPager.unregisterOnPageChangeCallback(it) }
        pageCallback = null
        _binding = null
        super.onDestroy()
    }

    fun goNext() {
        val cur = binding.viewPager.currentItem

        binding.viewPager.setCurrentItem(cur + 1, false)

    }

    fun goToNextPage(position: Int) {
        if (!isOnboardingNavigationEnabled) return
        if (isTimerNavigationLocked) return

        val lastIndex = (binding.viewPager.adapter?.itemCount ?: 1) - 1
        if (position in 0..lastIndex) {
            binding.viewPager.currentItem = position
        }
    }


    private fun setOnboardingNavigationEnabled(enabled: Boolean) {
        isOnboardingNavigationEnabled = enabled
        binding.viewPager.isUserInputEnabled = enabled
    }


    fun getCurrentPage(): Int = binding.viewPager.currentItem

    private fun loadInterstitial() {
        // keep your ad load here
    }

    // ✅ make it public if fragments call it
    fun nextClicked() {
        if (!isOnboardingNavigationEnabled) return
        if (isTimerNavigationLocked) return

        val lastIndex = (binding.viewPager.adapter?.itemCount ?: 1) - 1
        if (binding.viewPager.currentItem >= lastIndex) {
            endHelp()
        } else {
            binding.viewPager.setCurrentItem(binding.viewPager.currentItem + 1, true)
        }
    }

    private fun endHelp() {
        if (!baseConfig.appStarted) {
            baseConfig.appStarted = true
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
