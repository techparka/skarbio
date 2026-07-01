package com.budgetapp.ocr.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.budgetapp.ocr.R
import com.budgetapp.ocr.databinding.BottomSheetHeroMetricBinding
import com.budgetapp.ocr.databinding.ItemHeroMetricOptionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class HeroMetricPickerSheet(
    private val options: List<HeroOption>,
    private val currentMetric: HeroMetric,
    private val currentAccountId: Long,
    private val onSelect: (HeroMetric, Long) -> Unit
) : BottomSheetDialogFragment() {

    data class HeroOption(
        val metric: HeroMetric,
        val accountId: Long = -1L,
        val title: String,
        val subtitle: String,
        val emojiIcon: String? = null,
        val drawableIconRes: Int = -1,
        val iconBgColor: String = "#6366F1"
    )

    private var _binding: BottomSheetHeroMetricBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetHeroMetricBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildOptions()
    }

    private fun buildOptions() {
        options.forEach { option ->
            val itemBinding = ItemHeroMetricOptionBinding.inflate(
                layoutInflater, binding.containerOptions, true
            )

            val isSelected = option.metric == currentMetric &&
                    (option.metric != HeroMetric.ACCOUNT_BALANCE || option.accountId == currentAccountId)

            // Icon background color — prefix alpha: #33RRGGBB = ~20% opacity
            try {
                val hex = option.iconBgColor.removePrefix("#")
                val colorWithAlpha = Color.parseColor("#33$hex")
                itemBinding.iconBg.setBackgroundResource(R.drawable.bg_setting_icon)
                itemBinding.iconBg.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(colorWithAlpha)
            } catch (e: Exception) {
                // fallback - leave default
            }

            // Show emoji or drawable icon
            if (option.emojiIcon != null) {
                itemBinding.tvOptionEmoji.isVisible = true
                itemBinding.imgOptionIcon.isVisible = false
                itemBinding.tvOptionEmoji.text = option.emojiIcon
            } else if (option.drawableIconRes != -1) {
                itemBinding.imgOptionIcon.isVisible = true
                itemBinding.tvOptionEmoji.isVisible = false
                itemBinding.imgOptionIcon.setImageResource(option.drawableIconRes)
                itemBinding.imgOptionIcon.imageTintList =
                    android.content.res.ColorStateList.valueOf(
                        Color.parseColor(option.iconBgColor)
                    )
            }

            itemBinding.tvOptionTitle.text = option.title
            itemBinding.tvOptionSubtitle.text = option.subtitle

            // Highlight selected
            if (isSelected) {
                itemBinding.root.setBackgroundResource(R.drawable.bg_hero_option_selected)
                itemBinding.imgCheck.isVisible = true
                itemBinding.tvOptionTitle.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.primary)
                )
            } else {
                itemBinding.root.setBackgroundResource(R.drawable.bg_hero_option)
                itemBinding.imgCheck.isVisible = false
                itemBinding.tvOptionTitle.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.text_primary)
                )
            }

            itemBinding.root.setOnClickListener {
                onSelect(option.metric, option.accountId)
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
