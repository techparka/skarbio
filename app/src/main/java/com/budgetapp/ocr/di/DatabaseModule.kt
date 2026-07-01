package com.budgetapp.ocr.di

import android.content.Context
import com.budgetapp.ocr.data.local.AppDatabase
import com.budgetapp.ocr.data.local.dao.AccountDao
import com.budgetapp.ocr.data.local.dao.BillDao
import com.budgetapp.ocr.data.local.dao.BudgetDao
import com.budgetapp.ocr.data.local.dao.CategoryDao
import com.budgetapp.ocr.data.local.dao.MerchantDao
import com.budgetapp.ocr.data.local.dao.SettingsDao
import com.budgetapp.ocr.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module dla Room Database
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideTransactionDao(database: AppDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    fun provideSettingsDao(database: AppDatabase): SettingsDao {
        return database.settingsDao()
    }

    @Provides
    fun provideBillDao(database: AppDatabase): BillDao {
        return database.billDao()
    }

    @Provides
    fun provideBudgetDao(database: AppDatabase): BudgetDao {
        return database.budgetDao()
    }

    @Provides
    fun provideAccountDao(database: AppDatabase): AccountDao {
        return database.accountDao()
    }

    @Provides
    fun provideMerchantDao(database: AppDatabase): MerchantDao {
        return database.merchantDao()
    }
}
