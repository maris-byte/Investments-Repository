package com.marisbyte.invest.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AssetEntity::class,
        CandleEntity::class,
        AnalysisEntity::class,
        HoldingEntity::class,
        TransactionEntity::class,
        AssistantTaskEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun candleDao(): CandleDao
    abstract fun analysisDao(): AnalysisDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun assistantTaskDao(): AssistantTaskDao

    companion object {

        /**
         * Version 2 bringt Alfreds Aufgabenliste. Bewusst eine echte Migration statt
         * eines Neuaufbaus: Depot und Buchungen sind nicht wiederherstellbar.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `assistant_tasks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`dueAt` INTEGER, " +
                        "`done` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "invest-tracker.db"
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
