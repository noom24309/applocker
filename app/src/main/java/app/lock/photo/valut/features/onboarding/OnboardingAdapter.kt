package app.lock.photo.valut.features.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.lock.photo.valut.databinding.ItemOnboardingBinding

class OnboardingAdapter(
    private val pages: List<OnboardingPage>
) : RecyclerView.Adapter<OnboardingAdapter.PageViewHolder>() {

    inner class PageViewHolder(
        private val binding: ItemOnboardingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(page: OnboardingPage) = with(binding) {
            icon.setImageResource(page.icon)
            title.setText(page.title)
            subtitle.setText(page.subtitle)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemOnboardingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    override fun getItemCount(): Int = pages.size
}
