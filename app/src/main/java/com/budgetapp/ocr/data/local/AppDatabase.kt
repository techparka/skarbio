package com.budgetapp.ocr.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.budgetapp.ocr.data.local.dao.AccountDao
import com.budgetapp.ocr.data.local.dao.BillDao
import com.budgetapp.ocr.data.local.dao.BudgetDao
import com.budgetapp.ocr.data.local.dao.CategoryDao
import com.budgetapp.ocr.data.local.dao.MerchantDao
import com.budgetapp.ocr.data.local.dao.SettingsDao
import com.budgetapp.ocr.data.local.dao.TransactionDao
import com.budgetapp.ocr.data.local.entity.AccountEntity
import com.budgetapp.ocr.data.local.entity.BillEntity
import com.budgetapp.ocr.data.local.entity.BudgetEntity
import com.budgetapp.ocr.data.local.entity.CategoryEntity
import com.budgetapp.ocr.data.local.entity.MerchantEntity
import com.budgetapp.ocr.data.local.entity.SettingEntity
import com.budgetapp.ocr.data.local.entity.TransactionEntity

/**
 * Room Database - Phase 1 schema (3 tables + bills).
 * Version 4: category names migrated from Polish display names to locale-independent CategoryKey names.
 */
@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        SettingEntity::class,
        BillEntity::class,
        BudgetEntity::class,
        AccountEntity::class,
        MerchantEntity::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(com.budgetapp.ocr.data.local.TypeConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun billDao(): BillDao
    abstract fun budgetDao(): BudgetDao
    abstract fun accountDao(): AccountDao
    abstract fun merchantDao(): MerchantDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN image_hash TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS bills (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        vendorName TEXT NOT NULL,
                        invoiceNumber TEXT NOT NULL,
                        amount REAL NOT NULL,
                        currency TEXT NOT NULL DEFAULT 'PLN',
                        category TEXT NOT NULL,
                        issueDate TEXT NOT NULL,
                        dueDate TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'UNPAID',
                        paymentDate TEXT,
                        bankAccount TEXT,
                        recurrence TEXT NOT NULL DEFAULT 'NONE',
                        notes TEXT NOT NULL DEFAULT '',
                        rawOcrText TEXT NOT NULL DEFAULT '',
                        reminderEnabled INTEGER NOT NULL DEFAULT 0,
                        reminderDaysBefore INTEGER NOT NULL DEFAULT 3,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_dueDate ON bills (dueDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_status ON bills (status)")
            }
        }

        /**
         * Migration v3→v4: Convert Polish category display names to CategoryKey enum names.
         * This enables locale-independent storage so UI can resolve localized names via string resources.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Polish name → CategoryKey.name mapping for all tables
                val categoryMapping = mapOf(
                    // Expense categories
                    "Zakupy spożywcze" to "GROCERIES",
                    "Paliwo" to "FUEL",
                    "Zdrowie" to "HEALTH",
                    "Restauracje" to "RESTAURANT",
                    "Odzież" to "CLOTHING",
                    "Elektronika" to "ELECTRONICS",
                    "Rozrywka" to "ENTERTAINMENT",
                    "Transport" to "TRANSPORT",
                    "Rachunki" to "BILLS",
                    "Uroda" to "BEAUTY",
                    "Dom i ogród" to "HOME",
                    "Zwierzęta" to "PETS",
                    "Edukacja" to "EDUCATION",
                    "Prezenty" to "GIFTS",
                    "Inne wydatki" to "OTHER_EXPENSE",
                    "Inne" to "OTHER_EXPENSE",
                    // Income categories
                    "Wynagrodzenie" to "SALARY",
                    "Freelance" to "FREELANCE",
                    "Prezent" to "GIFT_INCOME",
                    "Inne przychody" to "OTHER_INCOME",
                    // Bill categories
                    "Telekomunikacja" to "TELECOM",
                    "Energia" to "ENERGY",
                    "Woda" to "WATER",
                    "Gaz" to "GAS",
                    "Internet" to "INTERNET"
                )

                for ((polishName, keyName) in categoryMapping) {
                    // Escape single quotes in Polish names
                    val escaped = polishName.replace("'", "''")
                    db.execSQL("UPDATE transactions SET category = '$keyName' WHERE category = '$escaped'")
                    db.execSQL("UPDATE categories SET name = '$keyName' WHERE name = '$escaped'")
                    db.execSQL("UPDATE bills SET category = '$keyName' WHERE category = '$escaped'")
                }
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS budgets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        category TEXT NOT NULL,
                        yearMonth TEXT NOT NULL,
                        limitAmount REAL NOT NULL,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_budgets_category_yearMonth " +
                    "ON budgets (category, yearMonth)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create accounts table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL DEFAULT 'CHECKING',
                        icon TEXT NOT NULL DEFAULT '💳',
                        color TEXT NOT NULL DEFAULT '#6366F1',
                        is_default INTEGER NOT NULL DEFAULT 0,
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // 2. Insert default checking account
                db.execSQL("""
                    INSERT INTO accounts (id, name, type, icon, color, is_default, sort_order, created_at)
                    VALUES (1, 'Główne', 'CHECKING', '💳', '#6366F1', 1, 0, ${System.currentTimeMillis()})
                """.trimIndent())

                // 3. Add account_id and target_account_id to transactions
                db.execSQL("ALTER TABLE transactions ADD COLUMN account_id INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE transactions ADD COLUMN target_account_id INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_account_id ON transactions (account_id)")

                // 4. Add account_id to bills
                db.execSQL("ALTER TABLE bills ADD COLUMN account_id INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN receiptImagePath TEXT")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN recurringGroupId TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_category ON transactions (category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_merchant_name ON transactions (merchant_name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bills_account_id ON bills (account_id)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS merchants (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        nip TEXT NOT NULL,
                        name TEXT NOT NULL,
                        category TEXT,
                        source TEXT NOT NULL DEFAULT 'user',
                        usage_count INTEGER NOT NULL DEFAULT 1,
                        last_used INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_merchants_nip ON merchants (nip)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "budget_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Seed default account on fresh install
                            db.execSQL(
                                """INSERT INTO accounts (id, name, type, icon, color, is_default, sort_order, created_at)
                                   VALUES (1, 'Główne', 'CHECKING', '💳', '#6366F1', 1, 0, ${System.currentTimeMillis()})"""
                            )
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
