package com.budgetapp.ocr.di

import android.content.Context
import com.budgetapp.ocr.MLKitDocumentScanner
import com.budgetapp.ocr.ReceiptTextRecognizer
import com.budgetapp.ocr.data.local.dao.TransactionDao
import com.budgetapp.ocr.data.remote.VatApiService
import com.budgetapp.ocr.service.DuplicateDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for general application dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideReceiptTextRecognizer(
        @ApplicationContext context: Context
    ): ReceiptTextRecognizer {
        return ReceiptTextRecognizer(context)
    }

    @Provides
    @Singleton
    fun provideMLKitDocumentScanner(
        @ApplicationContext context: Context
    ): MLKitDocumentScanner {
        return MLKitDocumentScanner(context)
    }

    @Provides
    @Singleton
    fun provideDuplicateDetector(
        transactionDao: TransactionDao
    ): DuplicateDetector {
        return DuplicateDetector(transactionDao)
    }

    @Provides
    @Singleton
    fun provideVatApiService(): VatApiService {
        return VatApiService()
    }
}
