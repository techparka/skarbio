package com.budgetapp.ocr.ui.transactions

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.TransactionEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.databinding.ItemTransactionBinding
import com.budgetapp.ocr.ui.common.CategoryIcons
import com.budgetapp.ocr.utils.CurrencyFormatter

class TransactionAdapter(
    private val onItemClick: ((TransactionEntity) -> Unit)? = null,
    private val onItemLongClick: ((TransactionEntity) -> Unit)? = null,
    private val onSelectionChanged: (() -> Unit)? = null
) : ListAdapter<TransactionEntity, TransactionAdapter.ViewHolder>(DiffCallback()) {

    var currency: String = "PLN"

    var isSelectionMode = false
        set(value) {
            field = value
            if (!value) selectedItems.clear()
            notifyItemRangeChanged(0, itemCount)
        }

    private val selectedItems = mutableSetOf<Long>()

    fun toggleSelection(transaction: TransactionEntity) {
        if (selectedItems.contains(transaction.id)) {
            selectedItems.remove(transaction.id)
        } else {
            selectedItems.add(transaction.id)
        }
        notifyItemChanged(currentList.indexOf(transaction))
    }

    fun getSelectedTransactions(): List<TransactionEntity> {
        return currentList.filter { selectedItems.contains(it.id) }
    }

    fun getSelectedCount(): Int = selectedItems.size

    fun clearSelection() {
        selectedItems.clear()
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemTransactionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TransactionEntity) {
            val context = binding.root.context

            val localizedCategory = CategoryKey.localizedName(context, item.category)
            binding.tvMerchantName.text = item.merchantName ?: localizedCategory
            binding.tvCategory.text = localizedCategory
            binding.tvDate.text = item.date

            // Amount with color
            val prefix = if (item.type == TransactionType.EXPENSE) "-" else "+"
            val colorRes = if (item.type == TransactionType.EXPENSE) R.color.expense_red else R.color.income_green
            binding.tvAmount.text = "$prefix${CurrencyFormatter.format(item.amount, currency)}"
            binding.tvAmount.setTextColor(ContextCompat.getColor(context, colorRes))

            // Category icon
            binding.ivCategoryIcon.setImageResource(CategoryIcons.getIconRes(item.category))
            val categoryColor = ContextCompat.getColor(context, CategoryIcons.getColorRes(item.category))
            binding.ivCategoryIcon.setColorFilter(categoryColor)

            // Icon background circle
            val bgColor = ContextCompat.getColor(context, CategoryIcons.getBackgroundColorRes(item.category))
            val background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            binding.iconBackground.background = background

            // Selection mode
            if (isSelectionMode) {
                binding.cbSelect.visibility = View.VISIBLE
                binding.cbSelect.setOnCheckedChangeListener(null)
                binding.cbSelect.isChecked = selectedItems.contains(item.id)
                binding.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedItems.add(item.id) else selectedItems.remove(item.id)
                    onSelectionChanged?.invoke()
                }
            } else {
                binding.cbSelect.setOnCheckedChangeListener(null)
                binding.cbSelect.visibility = View.GONE
            }

            // Click handlers
            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    binding.cbSelect.isChecked = !binding.cbSelect.isChecked
                } else {
                    onItemClick?.invoke(item)
                }
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    onItemLongClick?.invoke(item)
                    true
                } else {
                    false
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TransactionEntity>() {
        override fun areItemsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity): Boolean {
            return oldItem == newItem
        }
    }
}
