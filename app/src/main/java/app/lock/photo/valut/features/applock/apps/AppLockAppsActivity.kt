package app.lock.photo.valut.features.applock.apps

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.commit
import app.lock.photo.valut.R
import app.lock.photo.valut.core.ui.BaseActivity
import app.lock.photo.valut.features.applock.model.AppFilter
import dagger.hilt.android.AndroidEntryPoint

/**
 * Standalone host for [AppLockAppsFragment]. The list itself lives in the fragment so the
 * App Lock tab in MainActivity and this screen share one implementation.
 */
@AndroidEntryPoint
class AppLockAppsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_host)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(
                    R.id.fragmentContainer,
                    AppLockAppsFragment.standalone(intent.getStringExtra(EXTRA_INITIAL_FILTER))
                )
            }
        }
    }

    companion object {
        private const val EXTRA_INITIAL_FILTER = "extra_initial_filter"

        fun intent(context: Context, initialFilter: AppFilter? = null) =
            Intent(context, AppLockAppsActivity::class.java).apply {
                if (initialFilter != null) putExtra(EXTRA_INITIAL_FILTER, initialFilter.name)
            }
    }
}
