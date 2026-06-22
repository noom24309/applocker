package com.wastickers.romantic.stickers.loveromance.ui.ob.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.lock.photo.valut.App
import app.lock.photo.valut.App.Companion.canRequestAd
import app.lock.photo.valut.R
import com.wastickers.romantic.stickers.loveromance.activities.onboarding.BaseFragment
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.loadAndReturnAd
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.showLoadedNativeAd
import app.lock.photo.valut.databinding.FragmentOnboarding2Binding
import app.lock.photo.valut.features.ob.OnBoardingActivity

class OnboardingFragment2 : BaseFragment() {

    private lateinit var binding: FragmentOnboarding2Binding
    private val remoteConfig=getRemoteConfig()
    
    private var isFullScrNativeAdLoaded = false
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentOnboarding2Binding.inflate(inflater, container, false)


        binding.ivChecked.setOnClickListener {
            val current = (activity as? OnBoardingActivity)?.getCurrentPage() ?: return@setOnClickListener
            (activity as? OnBoardingActivity)?.goToNextPage(current + 1)
        }
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showNativeOnBoardOne()
        if (RemoteConfig.isNativeOb3Enabled() && canRequestAd) {
            loadTutorialNativeAds()
        }


    }

    private fun showNativeOnBoardOne() {
        if ( RemoteConfig.isNativeOb2Enabled()
        ) {
            App.instance.nativeOb2Ad.observe(viewLifecycleOwner) { nativeAd ->
                if (nativeAd != null) {
                    Log.d("CheckingNativeAd", "nativeAd is NOT null (observer)")

                    showLoadedNativeAd(
                        requireContext(),
                        binding.flAdNative,
                        R.layout.custom_admob_native_layout_1,
                        nativeAd
                    )
                } else {
                    binding.flAdNative.visibility = View.GONE
                }
            }
        } else {
            binding.flAdNative.visibility = View.GONE

        }
    }

    private fun loadTutorialNativeAds() {
        if (RemoteConfig.isNativeOb3Enabled() && canRequestAd) {
            loadAndReturnAd(
                requireContext(),
                resources.getString(R.string.native_ob3),
                adResult = {
                    App.instance.nativeOb3Ad.value = it
                })
        }
    }

}
