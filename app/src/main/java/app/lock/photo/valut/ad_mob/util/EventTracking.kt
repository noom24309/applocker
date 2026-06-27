package com.wastickers.romantic.stickers.loveromance.ad_mob.util

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.analytics.ParametersBuilder
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

internal fun logEvent(name: String) {
    Log.d("EventTracking", "logEvent: $name")
    Firebase.analytics.logEvent(name, null)
}

internal inline fun logEvent(name: String, crossinline block: ParametersBuilder.() -> Unit) {
    val parametersBuilder = ParametersBuilder()
    block(parametersBuilder)
    Log.d("EventTracking", "logEvent: $name with ${parametersBuilder.bundle}")
    Firebase.analytics.logEvent(name, block)
}