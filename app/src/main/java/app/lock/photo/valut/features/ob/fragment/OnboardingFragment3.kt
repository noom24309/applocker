package com.wastickers.romantic.stickers.loveromance.ui.ob.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.wastickers.romantic.stickers.loveromance.activities.onboarding.BaseFragment
import app.lock.photo.valut.databinding.FragmentOnboarding3Binding
import com.wastickers.romantic.stickers.loveromance.helper.beVisible
import app.lock.photo.valut.features.ob.OnBoardingActivity

class OnboardingFragment3 : BaseFragment() {

    private lateinit var binding: FragmentOnboarding3Binding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOnboarding3Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.ivChecked.setOnClickListener {
            val current =
                (activity as? OnBoardingActivity)?.getCurrentPage() ?: return@setOnClickListener
            (activity as? OnBoardingActivity)?.goToNextPage(current + 1)
        }
        binding.flAdNative.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        binding.ivChecked.beVisible()
    }
}
