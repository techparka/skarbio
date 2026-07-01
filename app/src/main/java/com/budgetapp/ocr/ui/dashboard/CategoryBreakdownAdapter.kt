package com.budgetapp.ocr.ui.dashboard

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.databinding.ItemCategoryBreakdownBinding
import com.budgetapp.ocr.ui.common.CategoryIcons
import com.budgetapp.ocr.utils.CurrencyFormatter

class CategoryBreakdownAdapter(
    private val onItemClick: (CategorySummary) -> Unit = {}
) : ListAdapter<CategorySummary, CategoryBreakdownAdapter.ViewHolder>(DiffCallback()) {

    var currency: String = "PLN"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBreakdownBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position == itemCount - 1)
    }

    inner class ViewHolder(
        private val binding: ItemCategoryBreakdownBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CategorySummary, isLastItem: Boolean) {
            val context = binding.root.context
            binding.root.setOnClickListener { onItemClick(item) }

            binding.tvCategoryName.text = CategoryKey.localizedName(context, item.category)
            binding.tvAmount.text = CurrencyFormatter.format(item.amount, currency)
            binding.tvPercentage.text = context.getString(R.string.percent_of_total, item.percentage)

            // Progress bar
            binding.progressBar.progress = item.percentage.toInt()
            val categoryColor = ContextCompat.getColor(context, CategoryIcons.getColorRes(item.category))
            binding.progressBar.setIndicatorColor(categoryColor)
            binding.progressBar.trackColor = ContextCompat.getColor(context, com.budgetapp.ocr.R.color.divider)

            // Category icon
            binding.ivCategoryIcon.setImageResource(CategoryIcons.getIconRes(item.category))
            binding.ivCategoryIcon.setColorFilter(categoryColor)

            // Icon background circle
            val bgColor = ContextCompat.getColor(context, CategoryIcons.getBackgroundColorRes(item.category))
            val background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            binding.iconBackground.background = background

            // Hide divider on last item
            binding.viewDivider.visibility = if (isLastItem) View.GONE else View.VISIBLE
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CategorySummary>() {
        override fun areItemsTheSame(oldItem: CategorySummary, newItem: CategorySummary): Boolean {
            return oldItem.category == newItem.category
        }

        override fun areContentsTheSame(oldItem: CategorySummary, newItem: CategorySummary): Boolean {
            return oldItem == newItem
        }
    }
}
