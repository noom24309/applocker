package app.lock.photo.valut.features.ob

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class PagerAdapter(
    fa: FragmentActivity,
    private val pagesProvider: OnboardingPagesProvider
) : FragmentStateAdapter(fa) {

    override fun getItemCount(): Int = pagesProvider.pageCount

    override fun createFragment(position: Int): Fragment = pagesProvider.createFragment(position)
}
