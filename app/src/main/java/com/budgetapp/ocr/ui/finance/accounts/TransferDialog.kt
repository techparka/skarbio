package com.budgetapp.ocr.ui.finance.accounts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.AccountEntity
import com.budgetapp.ocr.databinding.DialogTransferBinding
import com.budgetapp.ocr.ui.shared.DirtyFormDialogFragment

class TransferDialog : DirtyFormDialogFragment() {

    private var _binding: DialogTransferBinding? = null
    private val binding get() = _binding!!

    private var accounts: List<AccountEntity> = emptyList()
    var onTransfer: ((fromId: Long, toId: Long, amount: Double, notes: String) -> Unit)? = null

    // Dirty form detection
    private var originalFromIdx: Int = 0
    private var originalToIdx: Int = 1
    private var originalAmount: Double = 0.0
    private var originalNotes: String = ""

    fun setAccounts(accounts: List<AccountEntity>) {
        this.accounts = accounts
    }

    // ── DirtyFormDialogFragment implementation ─────────────────────────────

    override fun isDirty(): Boolean {
        if (_binding == null) return false

        try {
            val currentFromIdx = binding.spinnerFrom.selectedItemPosition
            val currentToIdx = binding.spinnerTo.selectedItemPosition
            val currentAmount = binding.etAmount.text.toString().trim().replace(",", ".").toDoubleOrNull() ?: 0.0
            val currentNotes = binding.etNotes.text.toString().trim()

            return currentFromIdx != originalFromIdx
                    || currentToIdx != originalToIdx
                    || currentAmount != originalAmount
                    || currentNotes != originalNotes
        } catch (e: Exception) {
            return false
        }
    }

    override fun onSaveClicked() {
        executeTransfer()
    }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val names = accounts.map { "${it.icon} ${it.name}" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        binding.spinnerFrom.adapter = adapter
        binding.spinnerTo.adapter = adapter

        // Pre-select: from = first, to = second (if exists)
        if (accounts.size >= 2) {
            binding.spinnerFrom.setSelection(0)
            binding.spinnerTo.setSelection(1)
        }

        binding.btnCancel.setOnClickListener { handleDismissRequest() }
        binding.btnTransfer.setOnClickListener { executeTransfer() }

        // Save original values for dirty form detection
        originalFromIdx = binding.spinnerFrom.selectedItemPosition
        originalToIdx = binding.spinnerTo.selectedItemPosition
        originalAmount = binding.etAmount.text.toString().trim().replace(",", ".").toDoubleOrNull() ?: 0.0
        originalNotes = binding.etNotes.text.toString().trim()
    }

    private fun executeTransfer() {
        val fromIdx = binding.spinnerFrom.selectedItemPosition
        val toIdx = binding.spinnerTo.selectedItemPosition

        if (fromIdx < 0 || toIdx < 0 || fromIdx >= accounts.size || toIdx >= accounts.size) return

        val fromAccount = accounts[fromIdx]
        val toAccount = accounts[toIdx]

        if (fromAccount.id == toAccount.id) {
            Toast.makeText(requireContext(), R.string.transfer_error_same_account, Toast.LENGTH_SHORT).show()
            return
        }

        val amountText = binding.etAmount.text?.toString()?.trim() ?: ""
        val amount = amountText.replace(",", ".").toDoubleOrNull()
        if (amount == null || amount <= 0 || amount > 999_999.99) {
            Toast.makeText(requireContext(), R.string.transfer_error_amount, Toast.LENGTH_SHORT).show()
            return
        }

        val notes = binding.etNotes.text?.toString()?.trim() ?: ""

        onTransfer?.invoke(fromAccount.id, toAccount.id, amount, notes)
        forceDismiss()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
