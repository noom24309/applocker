package com.wastickers.romantic.stickers.loveromance.ui.ob.adapter

import androidx.fragment.app.Fragment
import com.wastickers.romantic.stickers.loveromance.ui.ob.fragment.NativeFullSrcFragment
import com.wastickers.romantic.stickers.loveromance.ui.ob.fragment.NativeFullSrcFragment2
import com.wastickers.romantic.stickers.loveromance.ui.ob.fragment.OnboardingFragment1
import com.wastickers.romantic.stickers.loveromance.ui.ob.fragment.OnboardingFragment2
import com.wastickers.romantic.stickers.loveromance.ui.ob.fragment.OnboardingFragment3
import com.wastickers.romantic.stickers.loveromance.ui.ob.fragment.OnboardingFragment4

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingPagesProvider @Inject constructor() {

    val pageCount: Int = 6

    fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> OnboardingFragment1()
            1 -> OnboardingFragment2()
            2-> NativeFullSrcFragment()
            3 -> OnboardingFragment3()
            4 -> NativeFullSrcFragment2()
            5 -> OnboardingFragment4()
            else -> OnboardingFragment1() // safety fallback
        }
    }
}
