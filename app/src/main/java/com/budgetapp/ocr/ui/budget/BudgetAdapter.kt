package com.budgetapp.ocr.ui.budget

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.databinding.ItemBudgetBinding
import com.budgetapp.ocr.utils.CurrencyFormatter
import kotlin.math.roundToInt

class BudgetAdapter(
    private val onItemClick: (BudgetStatus) -> Unit
) : ListAdapter<BudgetStatus, BudgetAdapter.ViewHolder>(DiffCallback) {

    var currency: String = "PLN"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBudgetBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, parent.context)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemBudgetBinding,
        private val context: Context
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(status: BudgetStatus) {
            val localizedCategory = CategoryKey.localizedName(context, status.category)
            binding.tvCategory.text = localizedCategory

            val pct = status.percentage.roundToInt().coerceAtMost(100)
            binding.progressBudget.progress = pct

            // Color coding
            val color = when {
                status.isOverBudget    -> R.color.expense_red
                status.percentage >= 75 -> R.color.warning_orange
                else                   -> R.color.income_green
            }
            binding.progressBudget.progressTintList =
                ContextCompat.getColorStateList(context, color)

            binding.tvAmounts.text = String.format(
                "%s / %s",
                CurrencyFormatter.formatRounded(status.spentAmount, currency),
                CurrencyFormatter.formatRounded(status.limitAmount, currency)
            )

            binding.tvPercentage.text = context.getString(
                R.string.budget_percentage,
                status.percentage.roundToInt()
            )

            binding.tvRemaining.text = if (status.isOverBudget) {
                context.getString(
                    R.string.budget_over,
                    CurrencyFormatter.formatRounded(status.spentAmount - status.limitAmount, currency)
                )
            } else {
                context.getString(
                    R.string.budget_remaining,
                    CurrencyFormatter.formatRounded(status.remaining, currency)
                )
            }
            binding.tvRemaining.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (status.isOverBudget) R.color.expense_red else R.color.text_secondary
                )
            )

            binding.root.setOnClickListener { onItemClick(status) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<BudgetStatus>() {
        override fun areItemsTheSame(a: BudgetStatus, b: BudgetStatus) =
            a.category == b.category && a.yearMonth == b.yearMonth
        override fun areContentsTheSame(a: BudgetStatus, b: BudgetStatus) = a == b
    }
}
