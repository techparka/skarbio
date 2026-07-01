package com.budgetapp.ocr.ui.shared

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.budgetapp.ocr.R

/**
 * Base class for dialogs with dirty form detection.
 * Shows confirmation dialog when user tries to close with unsaved changes.
 *
 * Dialog is non-cancelable by default — gesture back, hardware back, and
 * touch-outside are all intercepted. User must use Cancel or Save buttons.
 * If the form is clean, back key dismisses immediately.
 */
abstract class DirtyFormDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Block all system-initiated dismissals (gesture, touch outside).
        // Back key is handled separately via OnKeyListener.
        isCancelable = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Intercept hardware/gesture back key at the Dialog level.
        // This fires reliably on all devices including MIUI gesture nav.
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                handleDismissRequest()
                true
            } else {
                false
            }
        }
    }

    /**
     * Override this to indicate whether the form has unsaved changes.
     * Return true if there are changes that haven't been saved yet.
     */
    protected abstract fun isDirty(): Boolean

    /**
     * Called when user clicks save button in the unsaved-changes dialog.
     */
    protected abstract fun onSaveClicked()

    /**
     * Force dismiss without dirty check. Use this after saving
     * or when user explicitly chooses to discard changes.
     */
    protected fun forceDismiss() {
        super.dismiss()
    }

    /**
     * Optional: called when user clicks "Don't Save" in confirmation dialog.
     * Default is to dismiss without saving.
     */
    protected open fun onDiscardClicked() {
        forceDismiss()
    }

    /**
     * Show confirmation dialog when user tries to close with unsaved changes.
     */
    protected fun showUnsavedChangesDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.unsaved_changes_title))
            .setMessage(getString(R.string.unsaved_changes_message))
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                onSaveClicked()
            }
            .setNeutralButton(getString(R.string.unsaved_changes_discard)) { _, _ ->
                onDiscardClicked()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * Call this from your cancel button's click listener.
     * Also called when user presses back key.
     */
    protected fun handleDismissRequest() {
        if (isDirty()) {
            showUnsavedChangesDialog()
        } else {
            forceDismiss()
        }
    }
}
