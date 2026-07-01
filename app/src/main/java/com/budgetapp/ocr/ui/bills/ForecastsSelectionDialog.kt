package com.budgetapp.ocr.ui.bills

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.budgetapp.ocr.ForecastEntry
import com.budgetapp.ocr.R
import com.budgetapp.ocr.databinding.DialogForecastsSelectionBinding

/**
 * Dialog do wybierania prognoz z checkboxami (edycja przed zapisaniem)
 */
class ForecastsSelectionDialog : DialogFragment() {

    private var _binding: DialogForecastsSelectionBinding? = null
    private val binding get() = _binding!!

    private var forecasts: List<ForecastEntry> = emptyList()
    private var currency: String = "PLN"
    private var onAddCallback: ((List<ForecastEntry>) -> Unit)? = null
    private var onCancelCallback: (() -> Unit)? = null

    private lateinit var adapter: ForecastCheckboxAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogForecastsSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        updateTitle()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupRecyclerView() {
        adapter = ForecastCheckboxAdapter(forecasts, currency)
        binding.rvForecasts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvForecasts.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener {
            onCancelCallback?.invoke()
            dismiss()
        }

        binding.btnAdd.setOnClickListener {
            val selected = adapter.getSelectedForecasts()
            if (selected.isNotEmpty()) {
                onAddCallback?.invoke(selected)
                dismiss()
            } else {
                android.widget.Toast.makeText(
                    requireContext(),
                    getString(R.string.select_at_least_one),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateTitle() {
        val count = forecasts.size
        binding.tvTitle.text = getString(R.string.forecasts_found_count, count)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            forecasts: List<ForecastEntry>,
            currency: String = "PLN",
            onAdd: (List<ForecastEntry>) -> Unit,
            onCancel: () -> Unit = {}
        ): ForecastsSelectionDialog {
            return ForecastsSelectionDialog().apply {
                this.forecasts = forecasts
                this.currency = currency
                this.onAddCallback = onAdd
                this.onCancelCallback = onCancel
            }
        }
    }
}
