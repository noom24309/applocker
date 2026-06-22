package com.wastickers.romantic.stickers.loveromance.ui.ob.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.lock.photo.valut.App
import app.lock.photo.valut.R
import com.wastickers.romantic.stickers.loveromance.activities.onboarding.BaseFragment
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.showLoadedNativeAd
import app.lock.photo.valut.databinding.FragmentObScreen4Binding
import com.wastickers.romantic.stickers.loveromance.helper.beVisible
import app.lock.photo.valut.features.ob.OnBoardingActivity
import kotlinx.coroutines.flow.MutableStateFlow

class NativeFullSrcFragment : BaseFragment() {
    private lateinit var binding: FragmentObScreen4Binding
    var UINativeOnboarding: String = ""
    var isAlreadyCalledFullScreenAd = false
    private var nativeFullScreenPopulated = MutableStateFlow(false)
    private val remoteConfig = getRemoteConfig()
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentObScreen4Binding.inflate(layoutInflater)
        binding.btnClose.setOnClickListener {
            val viewPager = (activity as OnBoardingActivity).binding!!.viewPager
            if (viewPager.currentItem < (viewPager.adapter?.itemCount ?: 0) - 1) {
                viewPager.currentItem = viewPager.currentItem + 1
            }
        }
        return binding.root
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        handleNativeAd()

    }


    private fun handleNativeAd() {
        if (RemoteConfig.isNativeFullScreen1Enabled()) {
            App.instance.nativeFullScreen1.observe(viewLifecycleOwner) { nativeAd ->
                if (nativeAd != null) {
                    Log.d("CheckingNativeAd", "nativeAd is NOT null (observer)")

                    showLoadedNativeAd(
                        requireContext(),
                        binding.flAdNative,
                        R.layout.custom_native_full_screen,
                        nativeAd
                    )
                } else {
                    binding.flAdNative.visibility = View.GONE
                    binding.clEmpty.beVisible()

                }
            }
        } else {
            binding.flAdNative.visibility = View.GONE
            binding.clEmpty.visibility = View.VISIBLE
        }
    }




}
