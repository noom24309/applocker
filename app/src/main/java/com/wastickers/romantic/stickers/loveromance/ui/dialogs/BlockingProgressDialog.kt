package com.wastickers.romantic.stickers.loveromance.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.Window
import android.widget.FrameLayout
import android.widget.ProgressBar

/** Simple non-cancelable spinner overlay used while a blocking action is in progress. */
class BlockingProgressDialog(context: Context) {

    private val dialog = Dialog(context).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCancelable(false)
        val container = FrameLayout(context).apply {
            addView(ProgressBar(context), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
        }
        setContentView(container)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    fun show() {
        if (!dialog.isShowing) runCatching { dialog.show() }
    }

    fun dismiss() {
        if (dialog.isShowing) runCatching { dialog.dismiss() }
    }
}
