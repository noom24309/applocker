package app.lock.photo.valut.features.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import app.lock.photo.valut.R
import app.lock.photo.valut.core.ui.BaseActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Standalone host for [SettingsFragment]. The screen itself lives in the fragment so the
 * Settings tab in MainActivity and this screen share one implementation.
 */
@AndroidEntryPoint
class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_host)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragmentContainer, SettingsFragment.standalone())
            }
        }
    }

    companion object {
        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
