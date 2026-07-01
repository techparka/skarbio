package com.budgetapp.ocr.ui.reports

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.budgetapp.ocr.R
import com.budgetapp.ocr.databinding.ItemTopStoreBinding
import com.budgetapp.ocr.utils.CurrencyFormatter

class TopStoresAdapter :
    ListAdapter<StoreSummary, TopStoresAdapter.ViewHolder>(DiffCallback()) {

    var currency: String = "PLN"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTopStoreBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    inner class ViewHolder(
        private val binding: ItemTopStoreBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StoreSummary, rank: Int) {
            val context = binding.root.context
            binding.tvRank.text = "$rank."
            binding.tvStoreName.text = item.storeName
            binding.tvTransactionCount.text = context.getString(R.string.transactions_count, item.transactionCount)
            binding.tvAmount.text = CurrencyFormatter.format(item.totalAmount, currency)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<StoreSummary>() {
        override fun areItemsTheSame(a: StoreSummary, b: StoreSummary) = a.storeName == b.storeName
        override fun areContentsTheSame(a: StoreSummary, b: StoreSummary) = a == b
    }
}
