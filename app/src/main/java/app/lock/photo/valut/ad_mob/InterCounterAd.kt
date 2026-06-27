package app.lock.photo.valut.ad_mob

import android.app.Activity
import android.util.Log
import app.lock.photo.valut.ad_mob.isInterstitialAdOnScreen1
import com.ads.control.ads.VioAdmob
import com.ads.control.ads.VioAdmobCallback
import app.lock.photo.valut.App

object InterCounterAd {

    private const val TAG = "InterCounterAd"

    private var counter = 0
    private var isAdShowing = false

    operator fun invoke(
        activity: Activity,
        nextAction: () -> Unit
    ) {
        // Agar ad already screen par hai to next action chala do, counter mat badao
        if (isAdShowing) {
            Log.d(TAG, "Ad already showing, performing next action")
            nextAction.invoke()
            return
        }

        counter++

        Log.d(TAG, "Counter = $counter")

        // Har 3rd call par ad: 1, 4, 7, 10... (counter % 3 == 1)
        val shouldShowAd = counter % 3 == 1

        // 2,3,5,6,8,9... par ad skip hoga
        if (!shouldShowAd) {
            Log.d(TAG, "Skip ad, perform next action")
            nextAction.invoke()
            return
        }

        Log.d(TAG, "Show ad")

        VioAdmob.getInstance().forceShowInterstitial(
            activity,
            App.mInterstitialAdHome,
            object : VioAdmobCallback() {

                private var isNextActionDone = false

                private fun performNextActionOnce() {
                    if (!isNextActionDone) {
                        isNextActionDone = true
                        Log.d(TAG, "Next action perform")

                        if(!activity.isDestroyed && !activity.isFinishing){
                            nextAction.invoke()

                        }
                    }
                }

                override fun onNextAction() {
                    super.onNextAction()
                    if(!activity.isDestroyed && !activity.isFinishing){
                        performNextActionOnce()


                    }
                }

                override fun onInterstitialShow() {
                    super.onInterstitialShow()
                    isAdShowing = true
                    isInterstitialAdOnScreen1 =true
                    Log.d(TAG, "Ad showing")
                }

                override fun onAdClosed() {
                    super.onAdClosed()
                    isAdShowing = false
                    isInterstitialAdOnScreen1 =false

                    Log.d(TAG, "Ad closed")
                }
            }
        ,true)
    }

    fun resetCounter() {
        counter = 0
    }

    fun getCounter(): Int {
        return counter
    }
}