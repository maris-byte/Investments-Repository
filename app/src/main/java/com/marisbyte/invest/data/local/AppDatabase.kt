package com.marisbyte.invest.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AssetEntity::class,
        CandleEntity::class,
        AnalysisEntity::class,
        HoldingEntity::class,
        TransactionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun candleDao(): CandleDao
    abstract fun analysisDao(): AnalysisDao
    abstract fun portfolioDao(): PortfolioDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "invest-tracker.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
