package app.lock.photo.valut.features.ob

import androidx.fragment.app.Fragment
import app.lock.photo.valut.features.ob.fragment.NativeFullSrcOBFragment
import app.lock.photo.valut.features.ob.fragment.NativeFullSrcOBFragment2
import app.lock.photo.valut.features.ob.fragment.OnboardingFragment1
import app.lock.photo.valut.features.ob.fragment.OnboardingFragment2
import app.lock.photo.valut.features.ob.fragment.OnboardingFragment3
import app.lock.photo.valut.features.ob.fragment.OnboardingFragment4
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingPagesProvider @Inject constructor() {

    val pageCount: Int = 6

    fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> OnboardingFragment1()
            1 -> OnboardingFragment2()
            2 -> NativeFullSrcOBFragment()
            3 -> OnboardingFragment3()
            4 -> NativeFullSrcOBFragment2()
            5 -> OnboardingFragment4()
            else -> OnboardingFragment1()
        }
    }
}
