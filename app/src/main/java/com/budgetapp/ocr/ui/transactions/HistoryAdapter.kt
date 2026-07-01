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
import com.budgetapp.ocr.data.local.entity.BillEntity
import com.budgetapp.ocr.data.local.entity.BillStatus
import com.budgetapp.ocr.data.local.entity.TransactionEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.databinding.ItemHistoryBinding
import com.budgetapp.ocr.ui.common.CategoryIcons
import com.budgetapp.ocr.utils.CurrencyFormatter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class HistoryAdapter(
    private val onTransactionClick: (TransactionEntity) -> Unit,
    private val onBillClick: (BillEntity) -> Unit,
    private val onItemLongClick: (HistoryItem) -> Unit,
    private val onSelectionChanged: () -> Unit
) : ListAdapter<HistoryItem, HistoryAdapter.ViewHolder>(HistoryDiffCallback()) {

    var currency: String = "PLN"

    var isSelectionMode = false
        set(value) {
            field = value
            if (!value) selectedItems.clear()
            notifyItemRangeChanged(0, itemCount)
        }

    private val selectedItems = mutableSetOf<Pair<HistoryItem.ItemType, Long>>()

    fun toggleSelection(item: HistoryItem) {
        val key = item.itemType to item.itemId
        if (selectedItems.contains(key)) {
            selectedItems.remove(key)
        } else {
            selectedItems.add(key)
        }
        notifyItemChanged(currentList.indexOf(item))
    }

    fun getSelectedTransactions(): List<TransactionEntity> {
        return currentList.filterIsInstance<HistoryItem.TransactionItem>()
            .filter { selectedItems.contains(it.itemType to it.itemId) }
            .map { it.transaction }
    }

    fun getSelectedBills(): List<BillEntity> {
        return currentList.filterIsInstance<HistoryItem.BillItem>()
            .filter { selectedItems.contains(it.itemType to it.itemId) }
            .map { it.bill }
    }

    fun getSelectedCount(): Int = selectedItems.size

    fun hasOnlyBillsSelected(): Boolean {
        return selectedItems.isNotEmpty() &&
                selectedItems.all { it.first == HistoryItem.ItemType.BILL }
    }

    fun clearSelection() {
        selectedItems.clear()
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem) {
            val context = binding.root.context
            val isSelected = selectedItems.contains(item.itemType to item.itemId)

            when (item) {
                is HistoryItem.TransactionItem -> bindTransaction(item.transaction)
                is HistoryItem.BillItem -> bindBill(item.bill)
            }

            // Selection mode
            if (isSelectionMode) {
                binding.cbSelect.visibility = View.VISIBLE
                binding.cbSelect.setOnCheckedChangeListener(null)
                binding.cbSelect.isChecked = isSelected
                binding.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    val key = item.itemType to item.itemId
                    if (isChecked) selectedItems.add(key) else selectedItems.remove(key)
                    onSelectionChanged()
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
                    when (item) {
                        is HistoryItem.TransactionItem -> onTransactionClick(item.transaction)
                        is HistoryItem.BillItem -> onBillClick(item.bill)
                    }
                }
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    onItemLongClick(item)
                    true
                } else {
                    false
                }
            }
        }

        private fun bindTransaction(tx: TransactionEntity) {
            val context = binding.root.context

            // Hide bill-specific views
            binding.statusAccentBar.visibility = View.GONE
            binding.tvStatusBadge.visibility = View.GONE
            binding.tvTypeLabel.visibility = View.GONE

            val localizedCategory = CategoryKey.localizedName(context, tx.category)
            binding.tvTitle.text = tx.merchantName ?: localizedCategory
            binding.tvCategory.text = localizedCategory
            binding.tvDate.text = tx.date

            // Amount with color
            val prefix = if (tx.type == TransactionType.EXPENSE) "-" else "+"
            val colorRes = if (tx.type == TransactionType.EXPENSE) R.color.expense_red else R.color.income_green
            binding.tvAmount.text = "$prefix${CurrencyFormatter.format(tx.amount, currency)}"
            binding.tvAmount.setTextColor(ContextCompat.getColor(context, colorRes))

            // Category icon
            binding.ivCategoryIcon.setImageResource(CategoryIcons.getIconRes(tx.category))
            val categoryColor = ContextCompat.getColor(context, CategoryIcons.getColorRes(tx.category))
            binding.ivCategoryIcon.setColorFilter(categoryColor)

            // Icon background circle
            val bgColor = ContextCompat.getColor(context, CategoryIcons.getBackgroundColorRes(tx.category))
            val background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            binding.iconBackground.background = background
        }

        private fun bindBill(bill: BillEntity) {
            val context = binding.root.context

            // Show bill-specific views
            binding.statusAccentBar.visibility = View.VISIBLE
            binding.tvTypeLabel.visibility = View.VISIBLE
            binding.tvTypeLabel.text = context.getString(R.string.bill_type)

            binding.tvTitle.text = bill.vendorName
            binding.tvCategory.text = CategoryKey.localizedName(context, bill.category)
            binding.tvAmount.text = CurrencyFormatter.format(bill.amount, currency)

            // Due date with relative info
            binding.tvDate.text = formatDueDate(bill.dueDate, bill.status, bill.paymentDate)

            // Category icon
            binding.ivCategoryIcon.setImageResource(CategoryIcons.getIconRes(bill.category))
            val categoryColor = ContextCompat.getColor(context, CategoryIcons.getColorRes(bill.category))
            binding.ivCategoryIcon.setColorFilter(categoryColor)

            // Icon background circle
            val bgColor = ContextCompat.getColor(context, CategoryIcons.getBackgroundColorRes(bill.category))
            val background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            binding.iconBackground.background = background

            // Status colors
            when (bill.status) {
                BillStatus.UNPAID -> {
                    val daysUntilDue = daysUntilDue(bill.dueDate)
                    if (daysUntilDue != null && daysUntilDue < 0) {
                        setAccentBarColor(R.color.warning_orange)
                        binding.tvStatusBadge.visibility = View.VISIBLE
                        binding.tvStatusBadge.text = context.getString(R.string.status_overdue).uppercase()
                        setStatusBadgeBackground(R.color.warning_orange)
                    } else {
                        setAccentBarColor(R.color.expense_red)
                        val isDueThisMonth = isDueInCurrentMonth(bill.dueDate)
                        if (isDueThisMonth) {
                            binding.tvStatusBadge.visibility = View.VISIBLE
                            binding.tvStatusBadge.text = context.getString(R.string.status_to_pay).uppercase()
                            setStatusBadgeBackground(R.color.expense_red)
                        } else {
                            binding.tvStatusBadge.visibility = View.GONE
                        }
                    }
                    binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.expense_red))
                }
                BillStatus.OVERDUE -> {
                    setAccentBarColor(R.color.warning_orange)
                    binding.tvStatusBadge.visibility = View.VISIBLE
                    binding.tvStatusBadge.text = context.getString(R.string.status_overdue).uppercase()
                    setStatusBadgeBackground(R.color.warning_orange)
                    binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.warning_orange))
                }
                BillStatus.PAID -> {
                    setAccentBarColor(R.color.income_green)
                    binding.tvStatusBadge.visibility = View.VISIBLE
                    binding.tvStatusBadge.text = context.getString(R.string.status_paid).uppercase()
                    setStatusBadgeBackground(R.color.income_green)
                    binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.income_green))
                }
            }
        }

        private fun setAccentBarColor(colorRes: Int) {
            binding.statusAccentBar.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, colorRes)
            )
        }

        private fun setStatusBadgeBackground(colorRes: Int) {
            val bg = GradientDrawable().apply {
                setColor(ContextCompat.getColor(binding.root.context, colorRes))
                cornerRadius = 8f
            }
            binding.tvStatusBadge.background = bg
        }

        private fun formatDueDate(dueDate: String, status: BillStatus, paymentDate: String?): String {
            val ctx = binding.root.context
            if (status == BillStatus.PAID) {
                return if (!paymentDate.isNullOrEmpty()) {
                    ctx.getString(R.string.paid_on_date, paymentDate)
                } else {
                    ctx.getString(R.string.status_paid)
                }
            }

            val days = daysUntilDue(dueDate) ?: return ctx.getString(R.string.due_date_prefix, dueDate)

            return when {
                days < -1 -> ctx.getString(R.string.overdue_days, (-days).toInt())
                days == -1L -> ctx.getString(R.string.overdue_1_day)
                days == 0L -> ctx.getString(R.string.due_today)
                days == 1L -> ctx.getString(R.string.due_tomorrow)
                days <= 7 -> ctx.getString(R.string.due_in_days, days.toInt())
                else -> ctx.getString(R.string.due_date_prefix, dueDate)
            }
        }

        private fun daysUntilDue(dueDate: String): Long? {
            return try {
                val due = LocalDate.parse(dueDate)
                ChronoUnit.DAYS.between(LocalDate.now(), due)
            } catch (e: Exception) {
                null
            }
        }

        private fun isDueInCurrentMonth(dueDate: String): Boolean {
            return try {
                val due = LocalDate.parse(dueDate)
                val now = LocalDate.now()
                due.year == now.year && due.month == now.month
            } catch (e: Exception) {
                false
            }
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem.itemType == newItem.itemType && oldItem.itemId == newItem.itemId
        }

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}
