package com.budgetapp.ocr.data.repository

import com.budgetapp.ocr.data.local.dao.SettingsDao
import com.budgetapp.ocr.data.local.entity.SettingEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for settings (key-value store)
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao
) {

    fun getAllSettings(): Flow<List<SettingEntity>> {
        return settingsDao.getAllSettings()
    }

    suspend fun getSettingByKey(key: String): SettingEntity? {
        return settingsDao.getSettingByKey(key)
    }

    suspend fun getValueByKey(key: String): String? {
        return settingsDao.getValueByKey(key)
    }

    suspend fun getValueByKey(key: String, defaultValue: String): String {
        return settingsDao.getValueByKey(key) ?: defaultValue
    }

    suspend fun setSetting(key: String, value: String) {
        val setting = SettingEntity(key, value, System.currentTimeMillis())
        settingsDao.insert(setting)
    }

    suspend fun insert(setting: SettingEntity) {
        settingsDao.insert(setting)
    }

    suspend fun insertAll(settings: List<SettingEntity>) {
        settingsDao.insertAll(settings)
    }

    suspend fun update(setting: SettingEntity) {
        settingsDao.update(setting)
    }

    suspend fun delete(setting: SettingEntity) {
        settingsDao.delete(setting)
    }

    suspend fun deleteByKey(key: String) {
        settingsDao.deleteByKey(key)
    }

    /**
     * Common setting keys
     */
    object Keys {
        const val DEFAULT_CURRENCY = "default_currency"
        const val THEME = "theme"
        const val BILL_REMINDERS_ENABLED = "bill_reminders_enabled"
        const val LAST_BACKUP_DATE = "last_backup_date"
        const val ONBOARDING_COMPLETED = "onboarding_completed"
        const val HERO_METRIC = "hero_metric"
        const val HERO_ACCOUNT_ID = "hero_account_id"
        const val API_STORE_LOOKUP_ENABLED = "api_store_lookup_enabled"
        const val APP_LOCK_ENABLED = "app_lock_enabled"
    }
}
