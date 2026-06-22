package com.wastickers.romantic.stickers.loveromance.ui.ob.fragment
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.lock.photo.valut.App
import app.lock.photo.valut.R
import com.wastickers.romantic.stickers.loveromance.activities.onboarding.BaseFragment
import com.wastickers.romantic.stickers.loveromance.ads.InterstitialAds
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig
import com.wastickers.romantic.stickers.loveromance.ads.RemoteConfig.InterOB
import com.wastickers.romantic.stickers.loveromance.ads.nativeAds.showLoadedNativeAd
import app.lock.photo.valut.databinding.FragmentOnboarding4Binding
import com.wastickers.romantic.stickers.loveromance.helper.baseConfig
import com.wastickers.romantic.stickers.loveromance.ui.dialogs.BlockingProgressDialog
import app.lock.photo.valut.features.auth.unlock.ChooseUnlockMethodActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.jvm.java


class OnboardingFragment4 : BaseFragment() {

    lateinit var binding: FragmentOnboarding4Binding
    private val remoteConfig = getRemoteConfig()
    private lateinit var progress: BlockingProgressDialog
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOnboarding4Binding.inflate(inflater, container, false)
        progress = BlockingProgressDialog(activity!!)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showNativeOnBoardOne()

        binding.ivChecked.setOnClickListener {

            if (InterOB()) {
                val interAd = InterstitialAds(resources.getString(R.string.Inter_OB))
                interAd.loadAndShowAd(activity!!, showLoading = true) {
                    activity!!.baseConfig.appStarted=true
                    startActivity(Intent(activity!!, ChooseUnlockMethodActivity::class.java))
                    activity!!.finish()
                }
            } else {

                activity!!.baseConfig.appStarted=true
                startActivity(Intent(activity!!, ChooseUnlockMethodActivity::class.java))
                activity!!.finish()
            }


        }


    }

    private var nativeOnBoardOnePopulated = MutableStateFlow(false)
    private fun showNativeOnBoardOne() {
        if ( RemoteConfig.isNativeOb4Enabled()) {
            App.instance.nativeOb4Ad.observe(viewLifecycleOwner) { nativeAd ->
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


}
