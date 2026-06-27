package app.lock.photo.valut.features.ob.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import app.lock.photo.valut.features.ob.fragment.OnboardingFragment1
import app.lock.photo.valut.features.ob.fragment.OnboardingFragment2
import app.lock.photo.valut.features.ob.fragment.OnboardingFragment3
import app.lock.photo.valut.features.ob.fragment.OnboardingFragment4

class OnBoardingPagerAdapter(
    fragmentActivity: FragmentActivity,
) : FragmentStateAdapter(fragmentActivity) {

    private val fragments = listOf<Fragment>(
        OnboardingFragment1(),
        OnboardingFragment2(),
        OnboardingFragment3(),
        OnboardingFragment4()
    )

    override fun createFragment(position: Int): Fragment = fragments[position]
    override fun getItemCount(): Int = fragments.size
}
