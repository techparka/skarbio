package com.budgetapp.ocr

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.budgetapp.ocr.data.repository.CategoryRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class dla Hilt dependency injection
 */
@HiltAndroidApp
class BudgetApplication : Application() {

    @Inject
    lateinit var categoryRepository: CategoryRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Restore saved theme before any activity is created
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val nightMode = when (prefs.getString("theme", "system")) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark"  -> AppCompatDelegate.MODE_NIGHT_YES
            else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)

        // Initialize default categories on first launch
        applicationScope.launch {
            categoryRepository.initializeDefaultCategories()
        }
    }
}
