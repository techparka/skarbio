package com.budgetapp.ocr.ui.finance.accounts

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.AccountType
import com.budgetapp.ocr.databinding.ItemAccountBinding
import com.budgetapp.ocr.utils.CurrencyFormatter

class AccountsAdapter(
    private val onClick: (AccountWithBalance) -> Unit,
    private val onLongClick: (AccountWithBalance) -> Unit
) : ListAdapter<AccountWithBalance, AccountsAdapter.AccountViewHolder>(DIFF_CALLBACK) {

    var currency: String = "PLN"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = ItemAccountBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AccountViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AccountViewHolder(
        private val binding: ItemAccountBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AccountWithBalance) {
            val ctx = binding.root.context
            val account = item.account

            binding.tvAccountIcon.text = account.icon
            binding.tvAccountName.text = account.name
            binding.tvAccountType.text = when (account.type) {
                AccountType.CHECKING -> ctx.getString(R.string.account_type_checking)
                AccountType.SAVINGS -> ctx.getString(R.string.account_type_savings)
            }
            binding.tvAccountBalance.text = CurrencyFormatter.format(item.balance, currency)

            // Color the balance
            val balanceColor = if (item.balance >= 0) {
                ctx.getColor(R.color.income_green)
            } else {
                ctx.getColor(R.color.expense_red)
            }
            binding.tvAccountBalance.setTextColor(balanceColor)

            // Default badge
            binding.tvDefaultBadge.isVisible = account.isDefault

            // Click listeners
            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AccountWithBalance>() {
            override fun areItemsTheSame(old: AccountWithBalance, new: AccountWithBalance) =
                old.account.id == new.account.id

            override fun areContentsTheSame(old: AccountWithBalance, new: AccountWithBalance) =
                old == new
        }
    }
}
