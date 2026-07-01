package com.budgetapp.ocr.ui.settings

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.CategoryEntity
import com.budgetapp.ocr.data.local.entity.TransactionType
import com.budgetapp.ocr.data.model.CategoryKey
import com.budgetapp.ocr.databinding.ItemCategoryBinding
import com.budgetapp.ocr.databinding.ItemCategoryHeaderBinding

class CategoriesAdapter(
    private val onEdit: (CategoryEntity) -> Unit,
    private val onDelete: (CategoryEntity) -> Unit
) : ListAdapter<CategoryListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ENTRY = 1
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is CategoryListItem.Header -> VIEW_TYPE_HEADER
        is CategoryListItem.Entry -> VIEW_TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                ItemCategoryHeaderBinding.inflate(inflater, parent, false)
            )
            else -> EntryViewHolder(
                ItemCategoryBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is CategoryListItem.Header -> (holder as HeaderViewHolder).bind(item.type)
            is CategoryListItem.Entry -> (holder as EntryViewHolder).bind(item.entity)
        }
    }

    inner class HeaderViewHolder(
        private val binding: ItemCategoryHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(type: TransactionType) {
            binding.tvSectionHeader.setText(
                when (type) {
                    TransactionType.EXPENSE -> R.string.expense_type
                    TransactionType.INCOME -> R.string.income_type
                    TransactionType.BILL -> R.string.bill_type
                    else -> R.string.expense_type
                }
            )
        }
    }

    inner class EntryViewHolder(
        private val binding: ItemCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entity: CategoryEntity) {
            binding.tvEmoji.text = entity.icon

            binding.tvName.text = CategoryKey.localizedName(binding.root.context, entity.name)

            // Parse hex color for the swatch
            runCatching {
                binding.viewColorSwatch.background.mutate().setTint(Color.parseColor(entity.color))
            }

            if (entity.isSystem) {
                binding.ivLock.visibility = View.VISIBLE
                binding.btnEdit.visibility = View.GONE
                binding.btnDelete.visibility = View.GONE
                binding.btnEdit.setOnClickListener(null)
                binding.btnDelete.setOnClickListener(null)
            } else {
                binding.ivLock.visibility = View.GONE
                binding.btnEdit.visibility = View.VISIBLE
                binding.btnDelete.visibility = View.VISIBLE
                binding.btnEdit.setOnClickListener { onEdit(entity) }
                binding.btnDelete.setOnClickListener { onDelete(entity) }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CategoryListItem>() {
        override fun areItemsTheSame(old: CategoryListItem, new: CategoryListItem): Boolean {
            return when {
                old is CategoryListItem.Header && new is CategoryListItem.Header -> old.type == new.type
                old is CategoryListItem.Entry && new is CategoryListItem.Entry -> old.entity.id == new.entity.id
                else -> false
            }
        }

        override fun areContentsTheSame(old: CategoryListItem, new: CategoryListItem) = old == new
    }
}
