package com.budgetapp.ocr.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.budgetapp.ocr.R
import com.budgetapp.ocr.data.local.entity.CategoryEntity
import com.budgetapp.ocr.databinding.FragmentCategoriesBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CategoriesFragment : Fragment() {

    private var _binding: FragmentCategoriesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CategoriesViewModel by viewModels()
    private lateinit var adapter: CategoriesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        setupFab()
        observeState()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = CategoriesAdapter(
            onEdit = { entity -> showAddEditDialog(entity) },
            onDelete = { entity -> showDeleteConfirmation(entity) }
        )
        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CategoriesFragment.adapter
        }
    }

    private fun setupFab() {
        binding.fabAddCategory.setOnClickListener {
            showAddEditDialog(null)
        }
    }

    private fun showAddEditDialog(entity: CategoryEntity?) {
        AddEditCategoryDialog.newInstance(
            existing = entity,
            onSave = { name, emoji, hexColor, type ->
                if (entity == null) {
                    viewModel.addCategory(name, emoji, hexColor, type)
                } else {
                    viewModel.updateCategory(
                        entity.copy(name = name, icon = emoji, color = hexColor, type = type)
                    )
                }
            }
        ).show(childFragmentManager, "add_edit_category")
    }

    private fun showDeleteConfirmation(entity: CategoryEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_category_title)
            .setMessage(getString(R.string.delete_category_message, entity.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteCategory(entity.id)
                Toast.makeText(requireContext(), R.string.category_deleted, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.items)
                    binding.emptyState.visibility =
                        if (state.items.isEmpty() && !state.isLoading) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
