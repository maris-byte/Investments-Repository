package com.marisbyte.invest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY name")
    fun observeAll(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE isWatched = 1 ORDER BY name")
    fun observeWatched(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets")
    suspend fun getAll(): List<AssetEntity>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getById(id: String): AssetEntity?

    @Query("SELECT * FROM assets WHERE id = :id")
    fun observeById(id: String): Flow<AssetEntity?>

    @Query("SELECT COUNT(*) FROM assets")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(assets: List<AssetEntity>)

    @Query("UPDATE assets SET isWatched = :watched WHERE id = :id")
    suspend fun setWatched(id: String, watched: Boolean)
}

@Dao
interface CandleDao {
    @Query("SELECT * FROM candles WHERE assetId = :assetId ORDER BY time ASC")
    suspend fun getForAsset(assetId: String): List<CandleEntity>

    @Query("SELECT * FROM candles WHERE assetId = :assetId ORDER BY time ASC")
    fun observeForAsset(assetId: String): Flow<List<CandleEntity>>

    @Query("SELECT MAX(time) FROM candles WHERE assetId = :assetId")
    suspend fun latestTime(assetId: String): Long?

    @Upsert
    suspend fun upsertAll(candles: List<CandleEntity>)

    @Query("DELETE FROM candles WHERE assetId = :assetId AND time < :before")
    suspend fun deleteOlderThan(assetId: String, before: Long)
}

@Dao
interface AnalysisDao {
    @Query("SELECT * FROM analyses WHERE strategy = :strategy ORDER BY score DESC")
    fun observeByStrategy(strategy: String): Flow<List<AnalysisEntity>>

    @Query("SELECT * FROM analyses WHERE assetId = :assetId")
    fun observeForAsset(assetId: String): Flow<List<AnalysisEntity>>

    @Query("SELECT * FROM analyses WHERE assetId = :assetId AND strategy = :strategy")
    suspend fun get(assetId: String, strategy: String): AnalysisEntity?

    @Query("SELECT * FROM analyses WHERE strategy = :strategy ORDER BY score DESC LIMIT :limit")
    suspend fun topByStrategy(strategy: String, limit: Int): List<AnalysisEntity>

    @Query("SELECT MAX(analyzedAt) FROM analyses")
    fun observeLastAnalyzedAt(): Flow<Long?>

    @Upsert
    suspend fun upsert(analysis: AnalysisEntity)

    @Upsert
    suspend fun upsertAll(analyses: List<AnalysisEntity>)
}

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM holdings ORDER BY id")
    fun observeHoldings(): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings WHERE assetId = :assetId")
    suspend fun getHolding(assetId: String): HoldingEntity?

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE assetId = :assetId ORDER BY date ASC, id ASC")
    suspend fun transactionsForAsset(assetId: String): List<TransactionEntity>

    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Upsert
    suspend fun upsertHolding(holding: HoldingEntity)

    @Query("DELETE FROM holdings WHERE assetId = :assetId")
    suspend fun deleteHolding(assetId: String)
}
