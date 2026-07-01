package com.budgetapp.ocr.ui.settings

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
import com.budgetapp.ocr.data.local.entity.CategoryEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.databinding.DialogAddEditCategoryBinding
import com.budgetapp.ocr.ui.shared.DirtyFormDialogFragment

class AddEditCategoryDialog : DirtyFormDialogFragment() {

    private var _binding: DialogAddEditCategoryBinding? = null
    private val binding get() = _binding!!

    private var existing: CategoryEntity? = null
    private var onSaveCallback: ((name: String, emoji: String, hexColor: String, type: TransactionType) -> Unit)? = null

    private val presetColors = listOf(
        "#16A34A", "#EA580C", "#DC2626", "#DB2777",
        "#9333EA", "#2563EB", "#0891B2", "#92400E",
        "#475569", "#65A30D", "#64748B", "#F59E0B"
    )

    private val emojiOptions = listOf(
        // Shopping & Food
        "\uD83D\uDED2", "\uD83C\uDF55", "\uD83C\uDF54", "\u2615", "\uD83C\uDF7A", "\uD83C\uDF70", "\uD83C\uDF5C", "\uD83C\uDF4E",
        // Transport & Travel
        "\uD83D\uDE97", "\u26FD", "\uD83D\uDE8C", "\uD83D\uDE82", "\u2708\uFE0F", "\uD83D\uDEB2", "\uD83D\uDE95", "\uD83D\uDEA2",
        // Home & Bills
        "\uD83C\uDFE0", "\u26A1", "\uD83D\uDCA7", "\uD83D\uDD25", "\uD83C\uDF10", "\uD83D\uDCF1", "\uD83D\uDCFA", "\uD83D\uDEBF",
        // Health & Beauty
        "\uD83D\uDC8A", "\uD83C\uDFE5", "\uD83D\uDC84", "\uD83D\uDC88", "\uD83E\uDDD8", "\uD83C\uDFCB\uFE0F", "\uD83E\uDD7C", "\uD83D\uDE91",
        // Clothing & Shopping
        "\uD83D\uDC55", "\uD83D\uDC5F", "\uD83D\uDC5C", "\uD83D\uDC8D", "\uD83D\uDECD\uFE0F", "\uD83C\uDF81", "\uD83D\uDCE6", "\uD83C\uDF92",
        // Work & Education
        "\uD83D\uDCBC", "\uD83D\uDCBB", "\uD83D\uDCDA", "\uD83C\uDF93", "\uD83D\uDCDD", "\uD83D\uDCC4", "\uD83D\uDCB0", "\uD83D\uDCB5",
        // Entertainment & Pets
        "\uD83C\uDFAC", "\uD83C\uDFAE", "\uD83C\uDFB5", "\uD83C\uDFC8", "\uD83D\uDC3E", "\uD83D\uDC36", "\uD83C\uDFA8", "\uD83C\uDFAD",
        // Misc
        "\uD83D\uDD27", "\uD83D\uDCF7", "\uD83D\uDE80", "\u2B50", "\u2764\uFE0F", "\uD83C\uDF3F", "\uD83C\uDFE2", "\uD83D\uDDD3\uFE0F"
    )

    private var selectedEmoji = "\uD83D\uDCE6"  // 📦
    private var selectedColor = "#64748B"

    // Dirty form detection
    private var originalName: String = ""
    private var originalEmoji: String = "\uD83D\uDCE6"
    private var originalColor: String = "#64748B"
    private var originalType: TransactionType = TransactionType.EXPENSE

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddEditCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTitle()
        setupEmojiGrid()
        setupColorGrid()
        populateIfEditing()
        setupButtons()

        // Save original values for dirty form detection
        originalName = binding.etName.text.toString().trim()
        originalEmoji = selectedEmoji
        originalColor = selectedColor
        originalType = when {
            binding.chipBillType.isChecked -> TransactionType.BILL
            binding.chipIncomeType.isChecked -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }
    }

    // ── DirtyFormDialogFragment implementation ─────────────────────────────

    override fun isDirty(): Boolean {
        if (_binding == null) return false

        try {
            val currentName = binding.etName.text.toString().trim()
            val currentType = when {
                binding.chipBillType.isChecked -> TransactionType.BILL
                binding.chipIncomeType.isChecked -> TransactionType.INCOME
                else -> TransactionType.EXPENSE
            }

            return currentName != originalName
                    || selectedEmoji != originalEmoji
                    || selectedColor != originalColor
                    || currentType != originalType
        } catch (e: Exception) {
            return false
        }
    }

    override fun onSaveClicked() {
        save()
    }

    // ─────────────────────────────────────────────────────────────────────────

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

    private fun setupTitle() {
        binding.tvTitle.setText(
            if (existing == null) R.string.add_category else R.string.edit_category
        )
        binding.tvSelectedEmoji.text = selectedEmoji
        runCatching {
            binding.viewSelectedColor.background.mutate().setTint(Color.parseColor(selectedColor))
        }
    }

    private fun setupEmojiGrid() {
        val grid = binding.emojiGrid
        grid.columnCount = 8
        val sizePx = resources.getDimensionPixelSize(R.dimen.emoji_cell_size)

        emojiOptions.forEach { emoji ->
            val tv = TextView(requireContext()).apply {
                text = emoji
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                gravity = android.view.Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = sizePx
                    height = sizePx
                }
                setOnClickListener {
                    selectedEmoji = emoji
                    binding.tvSelectedEmoji.text = emoji
                }
            }
            grid.addView(tv)
        }
    }

    private fun setupColorGrid() {
        val sizePx = resources.getDimensionPixelSize(R.dimen.color_swatch_size)
        val marginPx = resources.getDimensionPixelSize(R.dimen.color_swatch_margin)

        presetColors.forEach { hex ->
            val swatch = View(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(sizePx, sizePx).apply {
                    setMargins(marginPx, marginPx, marginPx, marginPx)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hex))
                }
                setOnClickListener {
                    selectedColor = hex
                    binding.viewSelectedColor.background.mutate().setTint(Color.parseColor(hex))
                }
            }
            binding.colorGrid.addView(swatch)
        }
    }

    private fun populateIfEditing() {
        existing?.let { entity ->
            binding.etName.setText(entity.name)
            selectedEmoji = entity.icon
            binding.tvSelectedEmoji.text = entity.icon
            selectedColor = entity.color
            runCatching {
                binding.viewSelectedColor.background.mutate().setTint(Color.parseColor(entity.color))
            }
            when (entity.type) {
                TransactionType.INCOME -> binding.chipIncomeType.isChecked = true
                TransactionType.BILL -> binding.chipBillType.isChecked = true
                else -> binding.chipExpenseType.isChecked = true
            }
        }
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener { handleDismissRequest() }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun save() {
        val name = binding.etName.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(requireContext(), R.string.category_name_required, Toast.LENGTH_SHORT).show()
            return
        }
        val type = when {
            binding.chipBillType.isChecked -> TransactionType.BILL
            binding.chipIncomeType.isChecked -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }
        onSaveCallback?.invoke(name, selectedEmoji, selectedColor, type)
        forceDismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onSaveCallback = null
        _binding = null
    }

    companion object {
        fun newInstance(
            existing: CategoryEntity? = null,
            onSave: (name: String, emoji: String, hexColor: String, type: TransactionType) -> Unit
        ): AddEditCategoryDialog {
            return AddEditCategoryDialog().apply {
                this.existing = existing
                this.onSaveCallback = onSave
            }
        }
    }
}
