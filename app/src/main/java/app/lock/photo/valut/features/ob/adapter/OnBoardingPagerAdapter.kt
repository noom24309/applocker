package app.lock.photo.valut.features.ob.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.wastickers.romantic.stickers.loveromance.ui.ob.fragment.NativeFullSrcFragment
import com.wastickers.romantic.stickers.loveromance.ui.ob.fragment.NativeFullSrcFragment2
import com.wastickers.romantic.stickers.loveromance.ui.ob.fragment.OnboardingFragment1
import com.wastickers.romantic.stickers.loveromance.ui.ob.fragment.OnboardingFragment2
import com.wastickers.romantic.stickers.loveromance.ui.ob.fragment.OnboardingFragment3
import com.wastickers.romantic.stickers.loveromance.ui.ob.fragment.OnboardingFragment4

class OnBoardingPagerAdapter(
    fragmentActivity: FragmentActivity,
) : FragmentStateAdapter(fragmentActivity) {

    private val fragments = mutableListOf<Fragment>(
        OnboardingFragment1(),
        OnboardingFragment2(),
        OnboardingFragment3(),
        OnboardingFragment4()
    )

    // Call these before or after load; adapter rebuilds its list
    var showNativeFull1: Boolean = false
        set(value) {
            field = value
            rebuildFragments()
        }

    var showNativeFull2: Boolean = false
        set(value) {
            field = value
            rebuildFragments()
        }

    private fun rebuildFragments() {
        fragments.clear()
        fragments.add(OnboardingFragment1())
        fragments.add(OnboardingFragment2())
        if (showNativeFull1) fragments.add(NativeFullSrcFragment())
        fragments.add(OnboardingFragment3())
        if (showNativeFull2) fragments.add(NativeFullSrcFragment2())
        fragments.add(OnboardingFragment4())
        notifyDataSetChanged()
    }

    override fun createFragment(position: Int): Fragment = fragments[position]
    override fun getItemCount(): Int = fragments.size
}