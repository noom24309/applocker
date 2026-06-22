package com.wastickers.romantic.stickers.loveromance.helper

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.View

// ── View visibility helpers ─────────────────────────────────────────────────
fun View.beVisible() { visibility = View.VISIBLE }
fun View.beGone() { visibility = View.GONE }
fun View.beInvisible() { visibility = View.INVISIBLE }

// ── Network ─────────────────────────────────────────────────────────────────
fun Context.isNetworkAvailable(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** Object form expected by some ported screens: `NetworkUtils.isNetworkAvailable` (imported as a member). */
object NetworkUtils {
    fun Context.isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

// ── Lightweight persisted app config (SharedPreferences) ────────────────────
val Context.baseConfig: BaseConfig get() = BaseConfig.getInstance(this)

class BaseConfig private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var selectedLanguage: String?
        get() = prefs.getString(KEY_SELECTED_LANGUAGE, null)
        set(value) { prefs.edit().putString(KEY_SELECTED_LANGUAGE, value).apply() }

    var selectedLanguageName: String?
        get() = prefs.getString(KEY_SELECTED_LANGUAGE_NAME, null)
        set(value) { prefs.edit().putString(KEY_SELECTED_LANGUAGE_NAME, value).apply() }

    var appStarted: Boolean
        get() = prefs.getBoolean(KEY_APP_STARTED, false)
        set(value) { prefs.edit().putBoolean(KEY_APP_STARTED, value).apply() }

    companion object {
        private const val KEY_SELECTED_LANGUAGE = "selected_language"
        private const val KEY_SELECTED_LANGUAGE_NAME = "selected_language_name"
        private const val KEY_APP_STARTED = "app_started"

        @Volatile private var instance: BaseConfig? = null

        fun getInstance(context: Context): BaseConfig =
            instance ?: synchronized(this) {
                instance ?: BaseConfig(context).also { instance = it }
            }
    }
}
