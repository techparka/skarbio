package com.budgetapp.ocr.ui.bills

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
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.databinding.ItemBillBinding
import com.budgetapp.ocr.utils.CurrencyFormatter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class BillsListAdapter(
    private val onItemClick: (BillEntity) -> Unit,
    private val onItemLongClick: ((BillEntity) -> Boolean)? = null,
    private val onSelectionChanged: (() -> Unit)? = null
) : ListAdapter<BillEntity, BillsListAdapter.BillViewHolder>(BillDiffCallback()) {

    // Selection mode state
    var currency: String = "PLN"

    var isSelectionMode = false
        set(value) {
            field = value
            if (!value) selectedItems.clear()
            notifyItemRangeChanged(0, itemCount)
        }

    private val selectedItems = mutableSetOf<Long>()

    fun toggleSelection(bill: BillEntity) {
        if (selectedItems.contains(bill.id)) {
            selectedItems.remove(bill.id)
        } else {
            selectedItems.add(bill.id)
        }
        notifyItemChanged(currentList.indexOf(bill))
    }

    fun getSelectedBills(): List<BillEntity> {
        return currentList.filter { selectedItems.contains(it.id) }
    }

    fun getSelectedCount(): Int = selectedItems.size

    fun clearSelection() {
        selectedItems.clear()
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BillViewHolder {
        val binding = ItemBillBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BillViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BillViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BillViewHolder(
        private val binding: ItemBillBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(bill: BillEntity) {
            val context = binding.root.context

            binding.tvVendorName.text = bill.vendorName
            binding.tvInvoiceNumber.text = CategoryKey.localizedName(context, bill.category)
            binding.tvAmount.text = CurrencyFormatter.format(bill.amount, currency)

            // Due date with relative info
            val dueDateText = formatDueDate(bill.dueDate, bill.status, bill.paymentDate)
            binding.tvDueDate.text = dueDateText

            // Selection mode
            if (isSelectionMode) {
                binding.cbSelect.visibility = View.VISIBLE
                binding.cbSelect.setOnCheckedChangeListener(null)
                binding.cbSelect.isChecked = selectedItems.contains(bill.id)
                binding.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedItems.add(bill.id) else selectedItems.remove(bill.id)
                    onSelectionChanged?.invoke()
                }
            } else {
                binding.cbSelect.setOnCheckedChangeListener(null)
                binding.cbSelect.visibility = View.GONE
            }

            // Status colors
            when (bill.status) {
                BillStatus.UNPAID -> {
                    val daysUntilDue = daysUntilDue(bill.dueDate)
                    if (daysUntilDue != null && daysUntilDue < 0) {
                        // Overdue but not yet marked
                        setStatusColor(binding, R.color.warning_orange)
                        binding.tvStatusBadge.visibility = View.VISIBLE
                        binding.tvStatusBadge.text = context.getString(R.string.status_overdue).uppercase()
                        setStatusBadgeBackground(binding, R.color.warning_orange)
                    } else {
                        setStatusColor(binding, R.color.expense_red)
                        // "Do Zapłaty" badge only for bills due this month
                        val isDueThisMonth = isDueInCurrentMonth(bill.dueDate)
                        if (isDueThisMonth) {
                            binding.tvStatusBadge.visibility = View.VISIBLE
                            binding.tvStatusBadge.text = context.getString(R.string.status_to_pay).uppercase()
                            setStatusBadgeBackground(binding, R.color.expense_red)
                        } else {
                            binding.tvStatusBadge.visibility = View.GONE
                        }
                    }
                    binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.expense_red))
                }
                BillStatus.OVERDUE -> {
                    setStatusColor(binding, R.color.warning_orange)
                    binding.tvStatusBadge.visibility = View.VISIBLE
                    binding.tvStatusBadge.text = context.getString(R.string.status_overdue).uppercase()
                    setStatusBadgeBackground(binding, R.color.warning_orange)
                    binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.warning_orange))
                }
                BillStatus.PAID -> {
                    setStatusColor(binding, R.color.income_green)
                    binding.tvStatusBadge.visibility = View.VISIBLE
                    binding.tvStatusBadge.text = context.getString(R.string.status_paid).uppercase()
                    setStatusBadgeBackground(binding, R.color.income_green)
                    binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.income_green))
                }
            }

            // Click handlers
            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    // Toggle selection
                    binding.cbSelect.isChecked = !binding.cbSelect.isChecked
                } else {
                    // Normal click
                    onItemClick(bill)
                }
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    onItemLongClick?.invoke(bill) ?: false
                } else {
                    false
                }
            }
        }

        private fun setStatusColor(binding: ItemBillBinding, colorRes: Int) {
            binding.statusIndicator.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, colorRes)
            )
        }

        private fun setStatusBadgeBackground(binding: ItemBillBinding, colorRes: Int) {
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

    class BillDiffCallback : DiffUtil.ItemCallback<BillEntity>() {
        override fun areItemsTheSame(oldItem: BillEntity, newItem: BillEntity) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: BillEntity, newItem: BillEntity) =
            oldItem == newItem
    }
}
