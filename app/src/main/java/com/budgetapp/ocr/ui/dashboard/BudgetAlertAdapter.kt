package com.budgetapp.ocr.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.databinding.ItemBudgetAlertBinding

class BudgetAlertAdapter(
    private val onItemClick: ((BudgetAlertItem) -> Unit)? = null
) : ListAdapter<BudgetAlertItem, BudgetAlertAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemBudgetAlertBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val b: ItemBudgetAlertBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: BudgetAlertItem) {
            b.root.setOnClickListener { onItemClick?.invoke(item) }
            val ctx = b.root.context
            b.tvAlertCategory.text = CategoryKey.localizedName(ctx, item.category)
            b.tvAlertPercentage.text = ctx.getString(R.string.budget_percentage, item.percentage.toInt())

            val pct = item.percentage.toInt().coerceAtMost(100)
            b.progressAlert.progress = pct

            val alertColor = when {
                item.isOverBudget -> ContextCompat.getColor(ctx, R.color.expense_red)
                item.percentage >= 90f -> ContextCompat.getColor(ctx, R.color.warning_orange)
                else -> ContextCompat.getColor(ctx, R.color.warning_orange)
            }
            b.tvAlertPercentage.setTextColor(alertColor)
            b.progressAlert.progressTintList =
                android.content.res.ColorStateList.valueOf(alertColor)

            b.tvAlertAmounts.text = ctx.getString(
                R.string.budget_amounts,
                item.spentAmount,
                item.limitAmount
            )
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<BudgetAlertItem>() {
            override fun areItemsTheSame(a: BudgetAlertItem, b: BudgetAlertItem) =
                a.category == b.category
            override fun areContentsTheSame(a: BudgetAlertItem, b: BudgetAlertItem) = a == b
        }
    }
}
