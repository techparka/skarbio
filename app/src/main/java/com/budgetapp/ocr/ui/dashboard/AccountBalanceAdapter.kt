package com.budgetapp.ocr.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.budgetapp.ocr.R
import com.budgetapp.ocr.databinding.ItemAccountBalanceCompactBinding
import com.budgetapp.ocr.utils.CurrencyFormatter

class AccountBalanceAdapter(
    private val onClick: (AccountBalanceItem) -> Unit
) : ListAdapter<AccountBalanceItem, AccountBalanceAdapter.ViewHolder>(DIFF_CALLBACK) {

    var currency: String = "PLN"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAccountBalanceCompactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemAccountBalanceCompactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AccountBalanceItem) {
            binding.tvIcon.text = item.icon
            binding.tvName.text = item.name
            binding.tvBalance.text = CurrencyFormatter.format(item.balance, currency)

            val ctx = binding.root.context
            val balanceColor = if (item.balance >= 0) {
                ctx.getColor(R.color.income_green)
            } else {
                ctx.getColor(R.color.expense_red)
            }
            binding.tvBalance.setTextColor(balanceColor)

            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AccountBalanceItem>() {
            override fun areItemsTheSame(old: AccountBalanceItem, new: AccountBalanceItem) =
                old.accountId == new.accountId
            override fun areContentsTheSame(old: AccountBalanceItem, new: AccountBalanceItem) =
                old == new
        }
    }
}
