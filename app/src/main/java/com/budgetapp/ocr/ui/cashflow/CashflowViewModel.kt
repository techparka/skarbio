package com.budgetapp.ocr.ui.cashflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.BillEntity
import com.budgetapp.ocr.data.local.entity.BillStatus
import com.budgetapp.ocr.data.repository.AccountRepository
import com.budgetapp.ocr.data.repository.BillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class CashflowState(
    val unpaidTotal: Double = 0.0,
    val paidTotal: Double = 0.0,
    val projectedBalance: Double = 0.0,
    val items: List<CashflowListItem> = emptyList(),
    val isEmpty: Boolean = true
)

sealed class CashflowListItem {
    data class Header(val titleRes: Int, val section: CashflowSection) : CashflowListItem()
    data class BillItem(val bill: BillEntity) : CashflowListItem()
}

enum class CashflowSection { OVERDUE, THIS_WEEK, LATER, PAID }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CashflowViewModel @Inject constructor(
    private val billRepository: BillRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    fun previousMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    val state: StateFlow<CashflowState> = _selectedMonth.flatMapLatest { ym ->
        val ymStr = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        combine(
            billRepository.getBillsForMonth(ymStr),
            billRepository.getOverdueBills()
        ) { monthBills, overdueBills ->
            buildState(ym, monthBills, overdueBills)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CashflowState())

    private suspend fun buildState(
        ym: YearMonth,
        monthBills: List<BillEntity>,
        allOverdueBills: List<BillEntity>
    ): CashflowState {
        val today = LocalDate.now()
        val currentYm = YearMonth.now()
        val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

        // Overdue bills from previous months (only when viewing current month)
        val overdueFromPrevious = if (ym == currentYm) {
            allOverdueBills.filter { bill ->
                try {
                    val due = LocalDate.parse(bill.dueDate)
                    YearMonth.from(due) != currentYm
                } catch (e: Exception) {
                    false
                }
            }
        } else {
            emptyList()
        }

        // Categorize current month's bills
        val overdueCurrent = monthBills.filter { bill ->
            bill.status != BillStatus.PAID && isOverdue(bill, today)
        }
        val thisWeek = monthBills.filter { bill ->
            bill.status != BillStatus.PAID && !isOverdue(bill, today) && isDueThisWeek(bill, today, endOfWeek)
        }
        val later = monthBills.filter { bill ->
            bill.status != BillStatus.PAID && !isOverdue(bill, today) && !isDueThisWeek(bill, today, endOfWeek)
        }
        val paid = monthBills.filter { it.status == BillStatus.PAID }

        val allOverdue = (overdueFromPrevious + overdueCurrent).distinctBy { it.id }

        // Build list items
        val items = mutableListOf<CashflowListItem>()

        if (allOverdue.isNotEmpty()) {
            items.add(CashflowListItem.Header(R.string.cashflow_section_overdue, CashflowSection.OVERDUE))
            items.addAll(allOverdue.sortedBy { it.dueDate }.map { CashflowListItem.BillItem(it) })
        }
        if (thisWeek.isNotEmpty()) {
            items.add(CashflowListItem.Header(R.string.cashflow_section_this_week, CashflowSection.THIS_WEEK))
            items.addAll(thisWeek.sortedBy { it.dueDate }.map { CashflowListItem.BillItem(it) })
        }
        if (later.isNotEmpty()) {
            items.add(CashflowListItem.Header(R.string.cashflow_section_later, CashflowSection.LATER))
            items.addAll(later.sortedBy { it.dueDate }.map { CashflowListItem.BillItem(it) })
        }
        if (paid.isNotEmpty()) {
            items.add(CashflowListItem.Header(R.string.cashflow_section_paid, CashflowSection.PAID))
            items.addAll(paid.sortedBy { it.dueDate }.map { CashflowListItem.BillItem(it) })
        }

        val unpaidTotal = allOverdue.sumOf { it.amount } + thisWeek.sumOf { it.amount } + later.sumOf { it.amount }
        val paidTotal = paid.sumOf { it.amount }

        // Projected balance
        val defaultAccountId = accountRepository.getDefaultAccountId()
        val balance = accountRepository.getAccountBalance(defaultAccountId)
        val projectedBalance = balance - unpaidTotal

        return CashflowState(
            unpaidTotal = unpaidTotal,
            paidTotal = paidTotal,
            projectedBalance = projectedBalance,
            items = items,
            isEmpty = items.isEmpty()
        )
    }

    private fun isOverdue(bill: BillEntity, today: LocalDate): Boolean {
        if (bill.status == BillStatus.OVERDUE) return true
        return try {
            LocalDate.parse(bill.dueDate).isBefore(today)
        } catch (e: Exception) {
            false
        }
    }

    private fun isDueThisWeek(bill: BillEntity, today: LocalDate, endOfWeek: LocalDate): Boolean {
        return try {
            val due = LocalDate.parse(bill.dueDate)
            !due.isBefore(today) && !due.isAfter(endOfWeek)
        } catch (e: Exception) {
            false
        }
    }
}
