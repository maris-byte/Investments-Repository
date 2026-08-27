package com.marisbyte.invest.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Ein handelbares Instrument aus dem Universum (assets/universe.json). */
@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val name: String,
    val assetClass: String,
    val currency: String,
    val yahooSymbol: String,
    val stooqSymbol: String?,
    val coingeckoId: String?,
    val region: String,
    val isWatched: Boolean = false
)

/** Zwischengespeicherte Tageskerze. */
@Entity(
    tableName = "candles",
    primaryKeys = ["assetId", "time"],
    indices = [Index("assetId")]
)
data class CandleEntity(
    val assetId: String,
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

/** Bewertung eines Instruments fuer eine Strategie zu einem Zeitpunkt. */
@Entity(
    tableName = "analyses",
    primaryKeys = ["assetId", "strategy"],
    indices = [Index("strategy"), Index("score")]
)
data class AnalysisEntity(
    val assetId: String,
    val strategy: String,
    val score: Int,
    val previousScore: Int?,
    val rating: String,
    val confidence: Int,
    val lastClose: Double,
    val change1d: Double,
    val summary: String,
    /** Faktoren als JSON, damit die Detailansicht ohne Neuberechnung auskommt. */
    val factorsJson: String,
    val metricsJson: String,
    val tradePlanJson: String?,
    val analyzedAt: Long
)

/** Eine Position im Depot; die Stueckzahl ergibt sich aus den Transaktionen. */
@Entity(tableName = "holdings", indices = [Index(value = ["assetId"], unique = true)])
data class HoldingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: String,
    val quantity: Double,
    /** Durchschnittlicher Einstandskurs in der Waehrung des Instruments. */
    val averagePrice: Double,
    val note: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

/** Kauf oder Verkauf. Grundlage fuer Stueckzahl, Einstand und realisierte Gewinne. */
@Entity(tableName = "transactions", indices = [Index("assetId")])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: String,
    /** BUY oder SELL. */
    val type: String,
    val quantity: Double,
    val price: Double,
    val fee: Double,
    val date: Long,
    val note: String? = null
)
