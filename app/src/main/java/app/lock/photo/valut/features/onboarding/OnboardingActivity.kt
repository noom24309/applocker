package app.lock.photo.valut.features.onboarding

import app.lock.photo.valut.core.ui.BaseActivity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import app.lock.photo.valut.R
import app.lock.photo.valut.core.lock.LockExempt
import app.lock.photo.valut.databinding.ActivityOnboardingBinding
import app.lock.photo.valut.features.auth.ChooseUnlockMethodActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OnboardingActivity : BaseActivity(), LockExempt {

    private lateinit var binding: ActivityOnboardingBinding
    private val viewModel: OnboardingViewModel by viewModels()

    private val pages = listOf(
        OnboardingPage(R.drawable.ic_lock, R.string.onboarding_title_1, R.string.onboarding_subtitle_1),
        OnboardingPage(R.drawable.ic_photo, R.string.onboarding_title_2, R.string.onboarding_subtitle_2),
        OnboardingPage(R.drawable.ic_intruder, R.string.onboarding_title_3, R.string.onboarding_subtitle_3)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPager()
        setupIndicators()
        setupButtons()
        observeFinished()
        renderForPage(0)
    }

    private fun setupPager() {
        binding.viewPager.adapter = OnboardingAdapter(pages)
        binding.viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = renderForPage(position)
            }
        )
    }

    private fun setupIndicators() {
        binding.indicatorContainer.removeAllViews()
        repeat(pages.size) {
            val dot = ImageView(this).apply {
                setImageResource(R.drawable.bg_indicator_inactive)
                val params = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = resources.getDimensionPixelSize(R.dimen.space_s) }
                layoutParams = params
            }
            binding.indicatorContainer.addView(dot)
        }
    }

    private fun setupButtons() {
        binding.btnSkip.setOnClickListener { viewModel.completeOnboarding() }
        binding.btnBack.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current > 0) binding.viewPager.currentItem = current - 1
        }
        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < pages.lastIndex) {
                binding.viewPager.currentItem = current + 1
            } else {
                viewModel.completeOnboarding()
            }
        }
    }

    private fun renderForPage(position: Int) {
        val isLast = position == pages.lastIndex
        binding.btnBack.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        binding.btnSkip.isVisible = !isLast
        binding.btnNext.text = getString(if (isLast) R.string.get_started else R.string.next)

        for (i in 0 until binding.indicatorContainer.childCount) {
            val dot = binding.indicatorContainer.getChildAt(i) as ImageView
            dot.setImageResource(
                if (i == position) R.drawable.bg_indicator_active
                else R.drawable.bg_indicator_inactive
            )
        }
    }

    private fun observeFinished() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.finished.collect { finished ->
                    if (finished) {
                        startActivity(Intent(this@OnboardingActivity, ChooseUnlockMethodActivity::class.java))
                        finish()
                    }
                }
            }
        }
    }
}
