package com.wastickers.romantic.stickers.loveromance.activities.onboarding

import androidx.fragment.app.Fragment
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig

/** Base for the onboarding pager fragments; exposes Remote Config like the ported screens expect. */
open class BaseFragment : Fragment() {
    fun getRemoteConfig(): FirebaseRemoteConfig = Firebase.remoteConfig
}
