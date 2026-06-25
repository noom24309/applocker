package com.wastickers.romantic.stickers.loveromance.ui.ob.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.wastickers.romantic.stickers.loveromance.activities.onboarding.BaseFragment
import app.lock.photo.valut.databinding.FragmentOnboarding2Binding
import app.lock.photo.valut.features.ob.OnBoardingActivity

class OnboardingFragment2 : BaseFragment() {

    private lateinit var binding: FragmentOnboarding2Binding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOnboarding2Binding.inflate(inflater, container, false)

        binding.ivChecked.setOnClickListener {
            val current = (activity as? OnBoardingActivity)?.getCurrentPage() ?: return@setOnClickListener
            (activity as? OnBoardingActivity)?.goToNextPage(current + 1)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.flAdNative.visibility = View.GONE
    }
}
