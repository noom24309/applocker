package com.wastickers.romantic.stickers.loveromance.ui.ob.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.wastickers.romantic.stickers.loveromance.activities.onboarding.BaseFragment
import app.lock.photo.valut.databinding.FragmentOnboarding4Binding
import com.wastickers.romantic.stickers.loveromance.helper.baseConfig
import app.lock.photo.valut.features.auth.unlock.ChooseUnlockMethodActivity

class OnboardingFragment4 : BaseFragment() {

    lateinit var binding: FragmentOnboarding4Binding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOnboarding4Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.flAdNative.visibility = View.GONE

        binding.ivChecked.setOnClickListener {
            activity!!.baseConfig.appStarted = true
            startActivity(Intent(activity!!, ChooseUnlockMethodActivity::class.java))
            activity!!.finish()
        }
    }
}
