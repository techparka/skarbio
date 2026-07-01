package com.budgetapp.ocr.ui.budget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.os.bundleOf
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.CategoryEntity
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.databinding.DialogAddBudgetBinding
import com.budgetapp.ocr.ui.shared.DirtyFormDialogFragment

class AddEditBudgetDialog : DirtyFormDialogFragment() {

    private var _binding: DialogAddBudgetBinding? = null
    private val binding get() = _binding!!

    var onSave: ((category: String, limit: Double) -> Unit)? = null
    var onDelete: (() -> Unit)? = null

    /** All expense categories (system + custom) — set by caller before show(). */
    var allExpenseCategories: List<CategoryEntity> = emptyList()

    private val existingCategory: String? get() = arguments?.getString(ARG_CATEGORY)
    private val existingLimit: Double get() = arguments?.getDouble(ARG_LIMIT, 0.0) ?: 0.0

    /** Category names parallel to spinner items (for saving). */
    private var categoryNames: List<String> = emptyList()

    // Dirty form detection
    private var originalLimit: String = ""
    private var originalCategoryIndex: Int = 0

    override fun isDirty(): Boolean {
        if (_binding == null) return false
        return try {
            binding.etLimit.text.toString().trim() != originalLimit
                    || binding.spinnerCategory.selectedItemPosition != originalCategoryIndex
        } catch (_: Exception) { false }
    }

    override fun onSaveClicked() {
        saveBudget()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isEdit = existingCategory != null

        // Build category lists — system (localized) + custom (raw name)
        categoryNames = allExpenseCategories.map { it.name }
        val categoryLabels = allExpenseCategories.map { cat ->
            if (cat.isSystem) CategoryKey.localizedName(requireContext(), cat.name) else cat.name
        }

        binding.spinnerCategory.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, categoryLabels
        ).also { it.setDropDownViewResource(R.layout.item_spinner_dialog) }

        if (isEdit) {
            binding.tvDialogTitle.text = getString(R.string.edit_budget)
            val idx = categoryNames.indexOfFirst { it == existingCategory }.coerceAtLeast(0)
            binding.spinnerCategory.setSelection(idx)
            binding.spinnerCategory.isEnabled = false
            if (existingLimit > 0) binding.etLimit.setText(existingLimit.toInt().toString())
            binding.btnDelete.visibility = View.VISIBLE
        }

        binding.btnCancel.setOnClickListener { handleDismissRequest() }
        binding.btnSave.setOnClickListener { saveBudget() }
        binding.btnDelete.setOnClickListener {
            onDelete?.invoke()
            forceDismiss()
        }

        // Save originals for dirty detection
        originalLimit = binding.etLimit.text.toString().trim()
        originalCategoryIndex = binding.spinnerCategory.selectedItemPosition
    }

    private fun saveBudget() {
        val limit = binding.etLimit.text.toString().toDoubleOrNull()
        if (limit == null || limit <= 0 || limit > 999_999.99) {
            Toast.makeText(requireContext(), R.string.budget_limit_hint, Toast.LENGTH_SHORT).show()
            return
        }
        val category = categoryNames[binding.spinnerCategory.selectedItemPosition]
        onSave?.invoke(category, limit)
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

    companion object {
        private const val ARG_CATEGORY = "category"
        private const val ARG_LIMIT = "limit"

        fun newInstance(category: String? = null, limit: Double = 0.0) =
            AddEditBudgetDialog().apply {
                arguments = bundleOf(ARG_CATEGORY to category, ARG_LIMIT to limit)
            }
    }
}
