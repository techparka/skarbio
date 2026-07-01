package com.budgetapp.ocr.data.repository

import com.budgetapp.ocr.data.local.dao.MerchantDao
import com.budgetapp.ocr.data.local.entity.MerchantEntity
import com.budgetapp.ocr.data.remote.VatApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MerchantRepository @Inject constructor(
    private val merchantDao: MerchantDao,
    private val vatApiService: VatApiService,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Looks up a company name by NIP.
     * Priority: Room cache → API (if consent given) → null
     */
    suspend fun findByNip(nip: String): MerchantEntity? {
        // 1. Room cache
        val cached = merchantDao.findByNip(nip)
        if (cached != null) {
            merchantDao.incrementUsage(nip)
            return cached
        }

        // 2. API lookup (only if user gave consent)
        val apiEnabled = settingsRepository.getValueByKey(
            SettingsRepository.Keys.API_STORE_LOOKUP_ENABLED, "false"
        ) == "true"

        if (!apiEnabled) return null

        val apiName = vatApiService.lookupByNip(nip) ?: return null
        val merchant = MerchantEntity(nip = nip, name = apiName, source = "api")
        merchantDao.upsert(merchant)
        return merchant
    }

    /**
     * Saves/updates a merchant after user edit (learning from corrections).
     */
    suspend fun saveFromUserEdit(nip: String, name: String, category: String?) {
        val existing = merchantDao.findByNip(nip)
        val merchant = MerchantEntity(
            id = existing?.id ?: 0,
            nip = nip,
            name = name,
            category = category,
            source = "user",
            usageCount = (existing?.usageCount ?: 0) + 1
        )
        merchantDao.upsert(merchant)
    }
}
