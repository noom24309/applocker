package app.lock.photo.valut.features.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** UI model for a single onboarding slide. */
data class OnboardingPage(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val subtitle: Int
)
