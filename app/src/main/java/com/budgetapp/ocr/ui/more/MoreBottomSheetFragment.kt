package com.budgetapp.ocr.ui.more

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.budgetapp.ocr.databinding.BottomSheetMoreBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MoreBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetMoreBinding? = null
    private val binding get() = _binding!!

    private var onSettingsClick: (() -> Unit)? = null
    private var onCategoriesClick: (() -> Unit)? = null
    private var onExportClick: (() -> Unit)? = null
    private var onImportCsvClick: (() -> Unit)? = null
    private var onBackupClick: (() -> Unit)? = null
    private var onRestoreClick: (() -> Unit)? = null
    private var onClearDataClick: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.itemSettings.setOnClickListener {
            dismiss()
            onSettingsClick?.invoke()
        }
        binding.itemCategories.setOnClickListener {
            dismiss()
            onCategoriesClick?.invoke()
        }
        binding.itemExport.setOnClickListener {
            dismiss()
            onExportClick?.invoke()
        }
        binding.itemImportCsv.setOnClickListener {
            dismiss()
            onImportCsvClick?.invoke()
        }
        binding.itemBackup.setOnClickListener {
            dismiss()
            onBackupClick?.invoke()
        }
        binding.itemRestore.setOnClickListener {
            dismiss()
            onRestoreClick?.invoke()
        }
        binding.itemClearData.setOnClickListener {
            dismiss()
            onClearDataClick?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            onSettingsClick: () -> Unit,
            onCategoriesClick: () -> Unit,
            onExportClick: () -> Unit,
            onImportCsvClick: () -> Unit = {},
            onBackupClick: () -> Unit = {},
            onRestoreClick: () -> Unit = {},
            onClearDataClick: () -> Unit = {}
        ): MoreBottomSheetFragment {
            return MoreBottomSheetFragment().apply {
                this.onSettingsClick = onSettingsClick
                this.onCategoriesClick = onCategoriesClick
                this.onExportClick = onExportClick
                this.onImportCsvClick = onImportCsvClick
                this.onBackupClick = onBackupClick
                this.onRestoreClick = onRestoreClick
                this.onClearDataClick = onClearDataClick
            }
        }
    }
}
