/*
 * AperoNextGenSplashLoadingDialog.kt
 *
 * Brief full-screen "loading advertisement" dialog shown right before a splash
 * interstitial appears (Apero style). Built programmatically so the SDK carries no
 * layout-resource dependency.
 */

package com.apero.nextgen.AdsSdk.interstitial

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Non-cancelable full-screen loading dialog used during the splash ad hand-off. */
internal class AperoNextGenSplashLoadingDialog(activity: Activity) {

  private val dialog =
    Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
      requestWindowFeature(Window.FEATURE_NO_TITLE)
      setCancelable(false)

      val density = activity.resources.displayMetrics.density
      fun dp(value: Int) = (value * density).toInt()

      val container =
        LinearLayout(activity).apply {
          orientation = LinearLayout.VERTICAL
          gravity = Gravity.CENTER
          setBackgroundColor(Color.WHITE)
          layoutParams =
            ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

      container.addView(ProgressBar(activity))
      container.addView(
        TextView(activity).apply {
          text = "Loading advertisement…"
          setTextColor(Color.DKGRAY)
          gravity = Gravity.CENTER
          setPadding(0, dp(16), 0, 0)
        }
      )

      setContentView(container)
      window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
      window?.setLayout(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    }

  fun show() {
    if (!dialog.isShowing) runCatching { dialog.show() }
  }

  fun dismiss() {
    if (dialog.isShowing) runCatching { dialog.dismiss() }
  }
}
