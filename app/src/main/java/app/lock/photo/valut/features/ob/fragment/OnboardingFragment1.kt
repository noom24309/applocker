package com.wastickers.romantic.stickers.loveromance.ui.ob.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.firebase.remoteconfig.get
import app.lock.photo.valut.App
import app.lock.photo.valut.App.Companion.canRequestAd
import app.lock.photo.valut.R
import com.wastickers.romantic.stickers.loveromance.activities.onboarding.BaseFragment
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.loadAndReturnAd
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.showLoadedNativeAd
import app.lock.photo.valut.databinding.FragmentOnboarding1Binding
import com.wastickers.romantic.stickers.loveromance.helper.beInvisible
import com.wastickers.romantic.stickers.loveromance.helper.beVisible
import app.lock.photo.valut.features.ob.OnBoardingActivity

class OnboardingFragment1 : BaseFragment() {

    private lateinit var binding: FragmentOnboarding1Binding

    private val remoteConfig=getRemoteConfig()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentOnboarding1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ivChecked.setOnClickListener {
            val current = (activity as? OnBoardingActivity)?.getCurrentPage() ?: return@setOnClickListener
            (activity as? OnBoardingActivity)?.goToNextPage(current + 1)
        }


        Log.e("Native_onboarding", "onViewCreated: " + remoteConfig["nativeOnboard2"].asBoolean())

            binding.ivChecked.beVisible()


        showNativeOnBoardOne()

        loadOnBoardingOneNative2()



    }

    private fun showNativeOnBoardOne(){
        if ( RemoteConfig.isNativeOb1Enabled()) {
            App.instance.nativeOb1Ad.observe(viewLifecycleOwner) { nativeAd ->
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
    private fun loadOnBoardingOneNative2() {
        if (RemoteConfig.isNativeOb2Enabled() && canRequestAd) {
            loadAndReturnAd(
                requireContext(),
                resources.getString(R.string.native_ob2),
                adResult = {
                    App.instance.nativeOb2Ad.value = it
                })
        }
    }


}