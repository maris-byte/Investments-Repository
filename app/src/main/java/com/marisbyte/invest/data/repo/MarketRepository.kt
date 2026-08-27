package com.marisbyte.invest.data.repo

import com.marisbyte.invest.analysis.model.Candle
import com.marisbyte.invest.data.local.AssetDao
import com.marisbyte.invest.data.local.AssetEntity
import com.marisbyte.invest.data.local.CandleDao
import com.marisbyte.invest.data.remote.MarketDataProvider
import kotlinx.coroutines.flow.Flow

/**
 * Haelt die Kursdaten. Kerzen werden lokal gecacht; das Netz wird nur befragt, wenn
 * der Cache aelter als ein Handelstag ist oder zu wenig Historie enthaelt.
 */
class MarketRepository(
    private val assetDao: AssetDao,
    private val candleDao: CandleDao,
    private val provider: MarketDataProvider
) {

    fun observeAssets(): Flow<List<AssetEntity>> = assetDao.observeAll()

    fun observeWatchlist(): Flow<List<AssetEntity>> = assetDao.observeWatched()

    fun observeAsset(assetId: String): Flow<AssetEntity?> = assetDao.observeById(assetId)

    suspend fun getAsset(assetId: String): AssetEntity? = assetDao.getById(assetId)

    suspend fun setWatched(assetId: String, watched: Boolean) = assetDao.setWatched(assetId, watched)

    suspend fun cachedCandles(assetId: String): List<Candle> =
        candleDao.getForAsset(assetId).map { it.toCandle() }

    /**
     * Liefert die Kerzen eines Instruments und aktualisiert sie bei Bedarf.
     * [forceRefresh] erzwingt den Netzabruf (Pull-to-Refresh).
     */
    suspend fun candles(asset: AssetEntity, forceRefresh: Boolean = false): List<Candle> {
        val cached = candleDao.getForAsset(asset.id)
        val newest = cached.lastOrNull()?.time ?: 0L
        val stale = System.currentTimeMillis() - newest > STALE_AFTER_MILLIS
        if (!forceRefresh && cached.size >= MIN_HISTORY && !stale) {
            return cached.map { it.toCandle() }
        }

        val result = provider.load(asset)
        if (result.candles.isEmpty()) {
            // Kein Netz oder Quelle ausgefallen: lieber der alte Cache als gar nichts.
            return cached.map { it.toCandle() }
        }
        candleDao.upsertAll(result.candles.map { it.toEntity(asset.id) })
        candleDao.deleteOlderThan(asset.id, System.currentTimeMillis() - RETENTION_MILLIS)
        return result.candles
    }

    private companion object {
        const val MIN_HISTORY = 60
        const val STALE_AFTER_MILLIS = 20L * 60 * 60 * 1000        // 20 Stunden
        const val RETENTION_MILLIS = 3L * 365 * 24 * 60 * 60 * 1000 // 3 Jahre
    }
}
