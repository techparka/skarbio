package com.budgetapp.ocr.ui.cashflow

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
import com.budgetapp.ocr.databinding.ItemCashflowHeaderBinding
import com.budgetapp.ocr.utils.CurrencyFormatter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class CashflowAdapter(
    private val onBillClick: (BillEntity) -> Unit
) : ListAdapter<CashflowListItem, RecyclerView.ViewHolder>(CashflowDiffCallback()) {

    var currency: String = "PLN"

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is CashflowListItem.Header -> VIEW_TYPE_HEADER
        is CashflowListItem.BillItem -> VIEW_TYPE_BILL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                ItemCashflowHeaderBinding.inflate(inflater, parent, false)
            )
            else -> BillViewHolder(
                ItemBillBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is CashflowListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is CashflowListItem.BillItem -> (holder as BillViewHolder).bind(item.bill)
        }
    }

    inner class HeaderViewHolder(
        private val binding: ItemCashflowHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(header: CashflowListItem.Header) {
            binding.tvSectionTitle.text = binding.root.context.getString(header.titleRes)
            val colorRes = when (header.section) {
                CashflowSection.OVERDUE -> R.color.expense_red
                CashflowSection.THIS_WEEK -> R.color.primary
                CashflowSection.LATER -> R.color.text_secondary
                CashflowSection.PAID -> R.color.income_green
            }
            binding.sectionIndicator.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, colorRes)
            )
        }
    }

    inner class BillViewHolder(
        private val binding: ItemBillBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(bill: BillEntity) {
            val context = binding.root.context

            binding.tvVendorName.text = bill.vendorName
            binding.tvInvoiceNumber.text = CategoryKey.localizedName(context, bill.category)
            binding.tvAmount.text = CurrencyFormatter.format(bill.amount, currency)
            binding.tvDueDate.text = formatDueDate(bill.dueDate, bill.status, bill.paymentDate)

            // Hide selection checkbox
            binding.cbSelect.visibility = View.GONE

            // Status colors
            when (bill.status) {
                BillStatus.UNPAID -> {
                    val daysUntil = daysUntilDue(bill.dueDate)
                    if (daysUntil != null && daysUntil < 0) {
                        setStatusColor(R.color.warning_orange)
                        showBadge(R.string.status_overdue, R.color.warning_orange)
                        binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.warning_orange))
                    } else {
                        setStatusColor(R.color.expense_red)
                        showBadge(R.string.status_to_pay, R.color.expense_red)
                        binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.expense_red))
                    }
                }
                BillStatus.OVERDUE -> {
                    setStatusColor(R.color.warning_orange)
                    showBadge(R.string.status_overdue, R.color.warning_orange)
                    binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.warning_orange))
                }
                BillStatus.PAID -> {
                    setStatusColor(R.color.income_green)
                    showBadge(R.string.status_paid, R.color.income_green)
                    binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.income_green))
                }
            }

            binding.root.setOnClickListener { onBillClick(bill) }
        }

        private fun setStatusColor(colorRes: Int) {
            binding.statusIndicator.setBackgroundColor(
                ContextCompat.getColor(binding.root.context, colorRes)
            )
        }

        private fun showBadge(textRes: Int, colorRes: Int) {
            binding.tvStatusBadge.visibility = View.VISIBLE
            binding.tvStatusBadge.text = binding.root.context.getString(textRes).uppercase()
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
                ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(dueDate))
            } catch (e: Exception) {
                null
            }
        }
    }

    class CashflowDiffCallback : DiffUtil.ItemCallback<CashflowListItem>() {
        override fun areItemsTheSame(oldItem: CashflowListItem, newItem: CashflowListItem): Boolean {
            return when {
                oldItem is CashflowListItem.Header && newItem is CashflowListItem.Header ->
                    oldItem.section == newItem.section
                oldItem is CashflowListItem.BillItem && newItem is CashflowListItem.BillItem ->
                    oldItem.bill.id == newItem.bill.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: CashflowListItem, newItem: CashflowListItem): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_BILL = 1
    }
}
