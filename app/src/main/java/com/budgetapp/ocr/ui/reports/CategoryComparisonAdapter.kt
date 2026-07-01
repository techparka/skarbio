package com.budgetapp.ocr.ui.reports

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.databinding.ItemCategoryComparisonBinding
import com.budgetapp.ocr.utils.CurrencyFormatter

class CategoryComparisonAdapter :
    ListAdapter<CategoryComparison, CategoryComparisonAdapter.ViewHolder>(DiffCallback()) {

    var currency: String = "PLN"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryComparisonBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemCategoryComparisonBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CategoryComparison) {
            val context = binding.root.context
            binding.tvCategory.text = CategoryKey.localizedName(context, item.category)
            binding.tvCurrentAmount.text = CurrencyFormatter.formatRounded(item.currentMonth, currency)
            binding.tvPreviousAmount.text = CurrencyFormatter.formatRounded(item.previousMonth, currency)

            val change = item.change
            when {
                change > 5 -> {
                    binding.tvChange.text = String.format("+%.0f%%", change)
                    binding.tvChange.setTextColor(ContextCompat.getColor(context, R.color.expense_red))
                }
                change < -5 -> {
                    binding.tvChange.text = String.format("%.0f%%", change)
                    binding.tvChange.setTextColor(ContextCompat.getColor(context, R.color.income_green))
                }
                else -> {
                    binding.tvChange.text = "~"
                    binding.tvChange.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CategoryComparison>() {
        override fun areItemsTheSame(a: CategoryComparison, b: CategoryComparison) = a.category == b.category
        override fun areContentsTheSame(a: CategoryComparison, b: CategoryComparison) = a == b
    }
}
