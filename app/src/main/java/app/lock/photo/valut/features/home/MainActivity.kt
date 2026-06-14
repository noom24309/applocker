package app.lock.photo.valut.features.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import app.lock.photo.valut.R
import app.lock.photo.valut.databinding.ActivityMainBinding
import app.lock.photo.valut.features.settings.SettingsFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Host activity for the post-unlock experience. Switches between the home
 * dashboard and settings via the bottom navigation bar.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            showFragment(HomeFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showFragment(HomeFragment())
                R.id.nav_settings -> showFragment(SettingsFragment())
                else -> return@setOnItemSelectedListener false
            }
            true
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(binding.fragmentContainer.id, fragment)
        }
    }
}
