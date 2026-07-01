package com.budgetapp.ocr.ui.settings

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.budgetapp.ocr.BuildConfig
import com.budgetapp.ocr.R
import com.budgetapp.ocr.databinding.FragmentSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let { BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED }
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvAppVersion.text = getString(
            R.string.version_build_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.BUILD_TIMESTAMP
        )
        setupClickListeners()
        observeState()
    }

    private fun setupClickListeners() {
        binding.cardTheme.setOnClickListener { showThemeDialog() }
        binding.cardCurrency.setOnClickListener { showCurrencyDialog() }
        binding.cardLanguage.setOnClickListener { showLanguageDialog() }
        binding.cardBillReminders.setOnClickListener {
            viewModel.setBillRemindersEnabled(!viewModel.state.value.billRemindersEnabled)
        }
        binding.cardStoreLookup.setOnClickListener {
            viewModel.setStoreLookupEnabled(!viewModel.state.value.storeLookupEnabled)
        }
        binding.cardPrivacyPolicy.setOnClickListener {
            val url = getString(R.string.privacy_policy_url)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun showThemeDialog() {
        val labels = arrayOf(
            getString(R.string.system_theme),
            getString(R.string.light_theme),
            getString(R.string.dark_theme)
        )
        val tags = arrayOf("system", "light", "dark")
        val currentTheme = viewModel.state.value.theme
        val checked = tags.indexOf(currentTheme).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.theme))
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val tag = tags[which]
                viewModel.setTheme(tag)
                requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    .edit().putString("theme", tag).apply()
                val nightMode = when (tag) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark"  -> AppCompatDelegate.MODE_NIGHT_YES
                    else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(nightMode)
                dialog.dismiss()
            }
            .show()
    }

    private fun showCurrencyDialog() {
        val currencies = arrayOf("PLN", "EUR", "USD")
        val labels = arrayOf("PLN – Złoty", "EUR – Euro", "USD – Dolar")
        val currentCurrency = viewModel.state.value.defaultCurrency
        val checked = currencies.indexOf(currentCurrency).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.default_currency))
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                viewModel.setDefaultCurrency(currencies[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun showLanguageDialog() {
        val locales = AppCompatDelegate.getApplicationLocales()
        val currentLang = if (locales.isEmpty) "system" else locales[0]!!.language

        val labels = arrayOf(
            getString(R.string.language_system),
            getString(R.string.language_english),
            getString(R.string.language_polish)
        )
        val tags = arrayOf("system", "en", "pl")
        val checked = tags.indexOf(currentLang).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.language_setting))
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val newLocales = when (tags[which]) {
                    "en" -> LocaleListCompat.forLanguageTags("en")
                    "pl" -> LocaleListCompat.forLanguageTags("pl")
                    else -> LocaleListCompat.getEmptyLocaleList()
                }
                AppCompatDelegate.setApplicationLocales(newLocales)
                dialog.dismiss()
            }
            .show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.tvCurrencyValue.text = state.defaultCurrency
                    binding.tvThemeValue.text = when (state.theme) {
                        "light" -> getString(R.string.light_theme)
                        "dark"  -> getString(R.string.dark_theme)
                        else    -> getString(R.string.system_theme)
                    }
                    binding.switchBillReminders.isChecked = state.billRemindersEnabled
                    binding.switchStoreLookup.isChecked = state.storeLookupEnabled
                }
            }
        }
        updateLanguageLabel()
    }

    private fun updateLanguageLabel() {
        val locales = AppCompatDelegate.getApplicationLocales()
        binding.tvLanguageValue.text = when {
            locales.isEmpty              -> getString(R.string.language_system)
            locales[0]?.language == "en" -> getString(R.string.language_english)
            locales[0]?.language == "pl" -> getString(R.string.language_polish)
            else                         -> getString(R.string.language_system)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
