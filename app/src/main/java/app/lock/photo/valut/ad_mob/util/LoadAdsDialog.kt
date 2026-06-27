package app.lock.photo.valut.ad_mob.util

import android.content.Context
import android.view.View
import app.lock.photo.valut.R
import com.ads.control.dialog.PrepareLoadingAdsDialog

object LoadAdsDialog {
    private var loadAdsDialog: PrepareLoadingAdsDialog? = null

    fun showLoadAdsDialog(context: Context) = try {
        loadAdsDialog?.let { if (it.isShowing) it.cancel() }
        loadAdsDialog = PrepareLoadingAdsDialog(context).also { it.show() }
    } catch (ex: Exception) {
        ex.printStackTrace()
    }

    fun hideLoadingsText() = try {
        loadAdsDialog?.let { if (it.isShowing) it.hideLoading() }
    } catch (ex: Exception) {
        ex.printStackTrace()
    }

    fun dismissLoadAdsDialog() = try {
        loadAdsDialog?.let { if (it.isShowing) it.dismiss() }
    } catch (ex: Exception) {
        ex.printStackTrace()
    }

    private fun PrepareLoadingAdsDialog.hideLoading() {
        findViewById<View>(R.id.spin_kit).visibility = View.INVISIBLE
        findViewById<View>(R.id.loading_dialog_tv).visibility = View.INVISIBLE
    }
}
