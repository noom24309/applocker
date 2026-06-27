package com.ads.control.ads;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ads.control.ads.wrapper.ApAdError;
import com.ads.control.ads.wrapper.ApInterstitialAd;
import com.ads.control.ads.wrapper.ApNativeAd;
import com.ads.control.ads.wrapper.ApRewardItem;
import com.google.android.gms.ads.appopen.AppOpenAd;

public class VioAdmobCallback {
    public void onNextAction() {
    }

    public void onAdClosed() {
    }

    public void onAdFailedToLoad(@Nullable ApAdError adError) {
    }

    public void onAdFailedToShow(@Nullable ApAdError adError) {
    }

    public void onAdLeftApplication() {
    }

    public void onAdLoaded() {

    }

    // ad splash loaded when showSplashIfReady = false
    public void onAdSplashReady() {

    }

    public void onInterstitialLoad(@Nullable ApInterstitialAd interstitialAd) {

    }

    public void onAdClicked() {
    }

    public void onAdImpression() {
    }


    public void onNativeAdLoaded(@NonNull ApNativeAd nativeAd) {

    }

    public void onUserEarnedReward(@NonNull ApRewardItem rewardItem) {

    }

    public void onInterstitialShow() {

    }

    public void onNormalInterSplashLoaded() {

    }

    public void onInterPriorityLoaded(@Nullable ApInterstitialAd interstitialAd) {

    }

    public void onInterPriorityMediumLoaded(@Nullable ApInterstitialAd interstitialAd) {

    }

    public void onAdSplashPriorityReady() {

    }

    public void onAdSplashPriorityMediumReady() {

    }


    public void onAdPriorityFailedToLoad(@Nullable ApAdError adError) {
    }

    public void onAdPriorityMediumFailedToLoad(@Nullable ApAdError adError) {
    }

    public void onAdPriorityFailedToShow(@Nullable ApAdError adError) {
    }

    public void onAdPriorityMediumFailedToShow(@Nullable ApAdError adError) {
    }

    public void onAppOpenAdHighLoad(@Nullable AppOpenAd appOpenAd){

    }

    public void onAppOpenAdMediumLoad(@Nullable AppOpenAd appOpenAd){

    }
}
