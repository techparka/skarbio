package com.budgetapp.ocr.ui.scan

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.budgetapp.ocr.R
import com.budgetapp.ocr.databinding.DialogDuplicateWarningBinding

class DuplicateWarningDialog : DialogFragment() {

    private var _binding: DialogDuplicateWarningBinding? = null
    private val binding get() = _binding!!

    private var onAddAnywayCallback: (() -> Unit)? = null
    private var onCancelCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_BudgetOCR)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogDuplicateWarningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val warning = arguments?.getString(ARG_WARNING) ?: ""
        val confidence = arguments?.getInt(ARG_CONFIDENCE) ?: 0

        binding.tvWarningMessage.text = warning
        binding.tvConfidence.text = "$confidence%"

        binding.btnAddAnyway.setOnClickListener {
            onAddAnywayCallback?.invoke()
            dismiss()
        }

        binding.btnCancel.setOnClickListener {
            onCancelCallback?.invoke()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_WARNING = "warning"
        private const val ARG_CONFIDENCE = "confidence"

        fun newInstance(
            warning: String,
            confidence: Int,
            onAddAnyway: () -> Unit,
            onCancel: () -> Unit
        ): DuplicateWarningDialog {
            return DuplicateWarningDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_WARNING, warning)
                    putInt(ARG_CONFIDENCE, confidence)
                }
                this.onAddAnywayCallback = onAddAnyway
                this.onCancelCallback = onCancel
            }
        }
    }
}
