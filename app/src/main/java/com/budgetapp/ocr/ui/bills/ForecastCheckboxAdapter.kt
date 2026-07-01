package com.budgetapp.ocr.ui.bills

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.budgetapp.ocr.ForecastEntry
import com.budgetapp.ocr.R
import com.budgetapp.ocr.databinding.ItemForecastCheckboxBinding
import com.budgetapp.ocr.utils.CurrencyFormatter

/**
 * Adapter dla RecyclerView z prognozami i checkboxami
 */
class ForecastCheckboxAdapter(
    private val forecasts: List<ForecastEntry>,
    var currency: String = "PLN"
) : RecyclerView.Adapter<ForecastCheckboxAdapter.ViewHolder>() {

    // Track selection state for each forecast
    private val selectedStates = MutableList(forecasts.size) { true }  // All checked by default

    inner class ViewHolder(val binding: ItemForecastCheckboxBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemForecastCheckboxBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val forecast = forecasts[position]
        val binding = holder.binding

        val ctx = holder.binding.root.context
        binding.tvAmount.text = CurrencyFormatter.format(forecast.amount, currency)
        binding.tvDueDate.text = ctx.getString(R.string.due_date_prefix, forecast.dueDate)

        if (forecast.description.isNotBlank()) {
            binding.tvDescription.visibility = android.view.View.VISIBLE
            binding.tvDescription.text = forecast.description
        } else {
            binding.tvDescription.visibility = android.view.View.GONE
        }

        // Set checkbox state
        binding.cbSelected.isChecked = selectedStates[position]

        // Handle checkbox clicks
        binding.cbSelected.setOnCheckedChangeListener { _, isChecked ->
            selectedStates[position] = isChecked
        }

        // Allow clicking whole item to toggle checkbox
        binding.root.setOnClickListener {
            binding.cbSelected.isChecked = !binding.cbSelected.isChecked
        }
    }

    override fun getItemCount() = forecasts.size

    /**
     * Returns list of selected forecasts
     */
    fun getSelectedForecasts(): List<ForecastEntry> {
        return forecasts.filterIndexed { index, _ -> selectedStates[index] }
    }

    /**
     * Returns count of selected items
     */
    fun getSelectedCount(): Int {
        return selectedStates.count { it }
    }
}
