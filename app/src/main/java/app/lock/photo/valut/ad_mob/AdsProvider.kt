package app.lock.photo.valut.ad_mob


import app.lock.photo.valut.App
import app.lock.photo.valut.R
import com.wastickers.romantic.stickers.loveromance.ad_mob.adgroup.NativeAdGroup

object AdsProvider {

    val nativeLanguage = NativeAdGroup(
        App.instance.getString(R.string.NativeLanaguge) to "native_language_2",
        name = "native_language_group"
    )

    val nativeLanguageDup = NativeAdGroup(
        App.instance.getString(R.string.NativeLanagugeDup) to "native_language_dup_2",
        name = "native_language_group"
    )

    val nativeLanguageHindi = NativeAdGroup(
        App.instance.getString(R.string.NativeLanagugeOther) to "native_language_hindi_2",
        name = "native_language_hindi_group"
    )

    val nativeLanguageHindiDup = NativeAdGroup(
        App.instance.getString(R.string.NativeLanagugeOtherDup) to "native_language_hindi_Dup_2",
        name = "native_language_hindi_group"
    )
    val nativeLanguageEnglish = NativeAdGroup(
        App.instance.getString(R.string.NativeLanagugeOther) to "native_language_English_2",
        name = "native_language_English_group"
    )
    val nativeLanguageEnglishDup = NativeAdGroup(
        App.instance.getString(R.string.NativeLanagugeOtherDup) to "native_language_English_Dup_2",
        name = "native_language_English_group"
    )

    val nativeFulScreen = NativeAdGroup(
        App.instance.getString(R.string.OBFull) to "native_fullscreen_2_1",
        isFullScreen = true,
        name = "native_fullscreen_group"
    )

    val nativeFulScreen2 = NativeAdGroup(
        App.instance.getString(R.string.OBFull2) to "native_fullscreen_2_2",
        isFullScreen = true,
        name = "native_fullscreen_group"
    )

    val nativeOnBoarding = NativeAdGroup(
        App.instance.getString(R.string.OB1) to "Native_onboarding_1",
        name = "Native_onboarding_group"
    )

    val nativeOnBoarding1 = NativeAdGroup(
        App.instance.getString(R.string.OB2) to "Native_onboarding_2",
        name = "Native_onboarding_group"
    )
    val nativeOnBoardingTwo = NativeAdGroup(
        App.instance.getString(R.string.OB3) to "Native_onboarding_3",
        name = "Native_onboarding_Two_group"
    )
    val nativeOnBoardingThree = NativeAdGroup(
        App.instance.getString(R.string.OB4) to "Native_onboarding_4",
        name = "Native_onboarding_Two_group"
    )


    val nativeSplash = NativeAdGroup(
        App.instance.getString(R.string.nativeSplash) to "native_splash",
        name = "nativeDialog_group")


    val nativeHome = NativeAdGroup(App.instance.getString(R.string.nativeAll) to "native_Home", name = "grant_Home_group")

}