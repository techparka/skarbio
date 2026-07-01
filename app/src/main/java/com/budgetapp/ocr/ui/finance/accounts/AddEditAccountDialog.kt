package com.budgetapp.ocr.ui.finance.accounts

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.AccountEntity
import com.budgetapp.ocr.data.local.entity.AccountType
import com.budgetapp.ocr.databinding.DialogAddEditAccountBinding
import com.budgetapp.ocr.ui.shared.DirtyFormDialogFragment

class AddEditAccountDialog : DirtyFormDialogFragment() {

    private var _binding: DialogAddEditAccountBinding? = null
    private val binding get() = _binding!!

    private var existing: AccountEntity? = null
    var onSave: ((name: String, type: AccountType, icon: String, color: String, isDefault: Boolean) -> Unit)? = null

    private val presetColors = listOf(
        "#6366F1", "#2563EB", "#0891B2", "#059669",
        "#16A34A", "#65A30D", "#F59E0B", "#EA580C",
        "#DC2626", "#DB2777", "#9333EA", "#475569"
    )

    private val iconOptions = listOf(
        // Row 1: money & banking
        "\uD83D\uDCB3", "\uD83D\uDCB0", "\uD83C\uDFE6", "\uD83D\uDCB5",
        "\uD83D\uDCB8", "\uD83E\uDE99", "\uD83D\uDCBC", "\uD83D\uDCB4",
        // Row 2: savings & investments
        "\uD83D\uDC8E", "\uD83C\uDF1F", "\uD83D\uDCC8", "\uD83C\uDFC6",
        "\uD83E\uDD11", "\uD83D\uDCB2", "\uD83E\uDE79", "\uD83D\uDCB1",
        // Row 3: lifestyle & goals
        "\uD83C\uDFE0", "\uD83D\uDE97", "\u2708\uFE0F", "\uD83C\uDF93",
        "\uD83C\uDFE2", "\uD83D\uDCF1", "\uD83D\uDEE1\uFE0F", "\uD83C\uDFAF",
        // Row 4: misc
        "\uD83D\uDED2", "\uD83C\uDF34", "\uD83D\uDC76", "\uD83D\uDC68\u200D\uD83D\uDCBB",
        "\uD83C\uDFE5", "\uD83C\uDF81", "\u2B50", "\uD83D\uDD11"
    )

    private var selectedIcon = "\uD83D\uDCB3"  // 💳
    private var selectedColor = "#6366F1"

    // Dirty form detection
    private var originalName: String = ""
    private var originalType: AccountType = AccountType.CHECKING
    private var originalIcon: String = "\uD83D\uDCB3"
    private var originalColor: String = "#6366F1"
    private var originalIsDefault: Boolean = false

    fun setExisting(account: AccountEntity) {
        this.existing = account
    }

    // ── DirtyFormDialogFragment implementation ─────────────────────────────

    override fun isDirty(): Boolean {
        if (_binding == null) return false

        try {
            val currentName = binding.etAccountName.text.toString().trim()
            val currentType = if (binding.chipSavings.isChecked) AccountType.SAVINGS else AccountType.CHECKING
            val currentIsDefault = binding.cbDefault.isChecked

            return currentName != originalName
                    || currentType != originalType
                    || selectedIcon != originalIcon
                    || selectedColor != originalColor
                    || currentIsDefault != originalIsDefault
        } catch (e: Exception) {
            return false
        }
    }

    override fun onSaveClicked() {
        saveAccount()
    }

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddEditAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        existing?.let { account ->
            binding.tvDialogTitle.text = getString(R.string.edit_account)
            binding.etAccountName.setText(account.name)
            selectedIcon = account.icon
            selectedColor = account.color
            binding.cbDefault.isChecked = account.isDefault
            when (account.type) {
                AccountType.CHECKING -> binding.chipChecking.isChecked = true
                AccountType.SAVINGS -> binding.chipSavings.isChecked = true
            }
        }

        setupIconGrid()
        setupColorGrid()

        binding.btnCancel.setOnClickListener { handleDismissRequest() }
        binding.btnSave.setOnClickListener { saveAccount() }

        // Save original values for dirty form detection
        originalName = binding.etAccountName.text.toString().trim()
        originalType = if (binding.chipSavings.isChecked) AccountType.SAVINGS else AccountType.CHECKING
        originalIcon = selectedIcon
        originalColor = selectedColor
        originalIsDefault = binding.cbDefault.isChecked
    }

    private fun setupIconGrid() {
        val grid = GridLayout(requireContext()).apply {
            columnCount = 8
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val cellSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 40f, resources.displayMetrics
        ).toInt()

        iconOptions.forEach { icon ->
            val tv = TextView(requireContext()).apply {
                text = icon
                textSize = 22f
                gravity = android.view.Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cellSize
                    height = cellSize
                }
                setOnClickListener {
                    selectedIcon = icon
                    highlightSelectedIcon(grid)
                }
            }
            if (icon == selectedIcon) {
                tv.setBackgroundColor(Color.parseColor("#336366F1"))
            }
            grid.addView(tv)
        }

        binding.rvIcons.visibility = View.GONE
        (binding.rvIcons.parent as ViewGroup).addView(
            grid,
            (binding.rvIcons.parent as ViewGroup).indexOfChild(binding.rvIcons)
        )
    }

    private fun highlightSelectedIcon(grid: GridLayout) {
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i) as TextView
            child.setBackgroundColor(
                if (child.text == selectedIcon) Color.parseColor("#336366F1")
                else Color.TRANSPARENT
            )
        }
    }

    private fun setupColorGrid() {
        val grid = GridLayout(requireContext()).apply {
            columnCount = 6
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val cellSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 36f, resources.displayMetrics
        ).toInt()
        val margin = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics
        ).toInt()

        presetColors.forEach { hex ->
            val v = View(requireContext()).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cellSize
                    height = cellSize
                    setMargins(margin, margin, margin, margin)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hex))
                    if (hex == selectedColor) {
                        setStroke(
                            TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics
                            ).toInt(),
                            Color.WHITE
                        )
                    }
                }
                setOnClickListener {
                    selectedColor = hex
                    highlightSelectedColor(grid)
                }
            }
            grid.addView(v)
        }

        binding.rvColors.visibility = View.GONE
        (binding.rvColors.parent as ViewGroup).addView(
            grid,
            (binding.rvColors.parent as ViewGroup).indexOfChild(binding.rvColors)
        )
    }

    private fun highlightSelectedColor(grid: GridLayout) {
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            val hex = presetColors[i]
            child.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(hex))
                if (hex == selectedColor) {
                    setStroke(
                        TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics
                        ).toInt(),
                        Color.WHITE
                    )
                }
            }
        }
    }

    private fun saveAccount() {
        val name = binding.etAccountName.text?.toString()?.trim() ?: ""
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), R.string.account_name_required, Toast.LENGTH_SHORT).show()
            return
        }

        val type = if (binding.chipSavings.isChecked) AccountType.SAVINGS else AccountType.CHECKING
        val isDefault = binding.cbDefault.isChecked

        onSave?.invoke(name, type, selectedIcon, selectedColor, isDefault)
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
