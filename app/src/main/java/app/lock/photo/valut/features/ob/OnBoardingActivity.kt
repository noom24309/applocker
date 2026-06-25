package app.lock.photo.valut.features.ob

import android.os.Bundle
import androidx.viewpager2.widget.ViewPager2
import com.wastickers.romantic.stickers.loveromance.BaseClass
import app.lock.photo.valut.databinding.ActivityOnboardingBinding
import com.wastickers.romantic.stickers.loveromance.helper.baseConfig
import app.lock.photo.valut.features.ob.adapter.OnBoardingPagerAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnBoardingActivity : BaseClass() {

    var binding: ActivityOnboardingBinding? = null

    private lateinit var adapter: OnBoardingPagerAdapter
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

        binding?.viewPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                isSwipeRight = position + positionOffset > s
                s = position + positionOffset
            }
            override fun onPageSelected(position: Int) {}
        })
    }

    fun goToNextPage(position: Int) {
        if (position < (binding?.viewPager?.adapter?.itemCount ?: 0)) {
            binding?.viewPager?.currentItem = position
        }
    }

    fun getCurrentPage(): Int = binding?.viewPager?.currentItem!!
}
