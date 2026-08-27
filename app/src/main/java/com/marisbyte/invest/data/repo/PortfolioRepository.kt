package com.marisbyte.invest.data.repo

import com.marisbyte.invest.analysis.model.AssetClass
import com.marisbyte.invest.analysis.model.Rating
import com.marisbyte.invest.data.local.AnalysisDao
import com.marisbyte.invest.data.local.AssetDao
import com.marisbyte.invest.data.local.AssetEntity
import com.marisbyte.invest.data.local.HoldingEntity
import com.marisbyte.invest.data.local.PortfolioDao
import com.marisbyte.invest.data.local.TransactionEntity
import com.marisbyte.invest.analysis.model.Strategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Eine Depotposition inklusive Bewertung und Signal. */
data class PortfolioPosition(
    val asset: AssetEntity,
    val quantity: Double,
    val averagePrice: Double,
    val lastPrice: Double,
    val score: Int?,
    val confidence: Int?,
    val change1d: Double
) {
    val assetClass: AssetClass get() = AssetClass.fromKey(asset.assetClass)
    val invested: Double get() = quantity * averagePrice
    val marketValue: Double get() = quantity * lastPrice
    val profit: Double get() = marketValue - invested
    val profitPercent: Double get() = if (invested == 0.0) 0.0 else profit / invested * 100.0
    val rating: Rating? get() = score?.let { Rating.of(it) }

    /** Handlungsempfehlung aus Score und aktuellem Gewinn/Verlust. */
    val adviceDe: String
        get() = when {
            score == null -> "Noch keine Analyse"
            score >= 66 -> "Position halten oder aufstocken"
            score >= 55 -> "Halten"
            score >= 46 -> "Beobachten"
            score >= 35 -> "Reduzieren erwaegen"
            else -> "Ausstieg pruefen"
        }
}

/** Kennzahlen des gesamten Depots. */
data class PortfolioSummary(
    val positions: List<PortfolioPosition>,
    val totalValue: Double,
    val totalInvested: Double,
    val totalProfit: Double,
    val totalProfitPercent: Double,
    val dayChangePercent: Double,
    /** Anteil je Anlageklasse am Depotwert in Prozent. */
    val allocation: Map<AssetClass, Double>,
    /** Gewichteter Durchschnittsscore des Depots. */
    val portfolioScore: Int?
)

/**
 * Verwaltet Transaktionen und leitet daraus Positionen ab. Der Einstandskurs wird
 * nach der Durchschnittsmethode gefuehrt: Kaeufe erhoehen ihn gewichtet, Verkaeufe
 * reduzieren nur die Stueckzahl.
 */
class PortfolioRepository(
    private val portfolioDao: PortfolioDao,
    private val assetDao: AssetDao,
    private val analysisDao: AnalysisDao
) {

    fun observeTransactions(): Flow<List<TransactionEntity>> = portfolioDao.observeTransactions()

    fun observeSummary(strategy: Strategy): Flow<PortfolioSummary> =
        combine(
            portfolioDao.observeHoldings(),
            assetDao.observeAll(),
            analysisDao.observeByStrategy(strategy.name)
        ) { holdings, assets, analyses ->
            val assetsById = assets.associateBy { it.id }
            val analysesById = analyses.associateBy { it.assetId }
            val positions = holdings.mapNotNull { holding ->
                val asset = assetsById[holding.assetId] ?: return@mapNotNull null
                val analysis = analysesById[holding.assetId]
                PortfolioPosition(
                    asset = asset,
                    quantity = holding.quantity,
                    averagePrice = holding.averagePrice,
                    lastPrice = analysis?.lastClose ?: holding.averagePrice,
                    score = analysis?.score,
                    confidence = analysis?.confidence,
                    change1d = analysis?.change1d ?: 0.0
                )
            }.filter { it.quantity > 0.0 }
            summarize(positions)
        }

    private fun summarize(positions: List<PortfolioPosition>): PortfolioSummary {
        val totalValue = positions.sumOf { it.marketValue }
        val totalInvested = positions.sumOf { it.invested }
        val profit = totalValue - totalInvested
        val dayChange = if (totalValue == 0.0) 0.0 else {
            // Gewichteter Tagesertrag: Wert von gestern aus der Tagesveraenderung zurueckgerechnet.
            val yesterday = positions.sumOf { it.marketValue / (1.0 + it.change1d / 100.0) }
            if (yesterday == 0.0) 0.0 else (totalValue / yesterday - 1.0) * 100.0
        }
        val allocation = positions
            .groupBy { it.assetClass }
            .mapValues { (_, group) ->
                if (totalValue == 0.0) 0.0 else group.sumOf { it.marketValue } / totalValue * 100.0
            }
        val scored = positions.filter { it.score != null && it.marketValue > 0.0 }
        val portfolioScore = if (scored.isEmpty() || totalValue == 0.0) null else {
            scored.sumOf { it.score!! * it.marketValue }.div(scored.sumOf { it.marketValue })
                .toInt().coerceIn(1, 100)
        }
        return PortfolioSummary(
            positions = positions.sortedByDescending { it.marketValue },
            totalValue = totalValue,
            totalInvested = totalInvested,
            totalProfit = profit,
            totalProfitPercent = if (totalInvested == 0.0) 0.0 else profit / totalInvested * 100.0,
            dayChangePercent = dayChange,
            allocation = allocation,
            portfolioScore = portfolioScore
        )
    }

    /** Bucht eine Transaktion und schreibt die Position fort. */
    suspend fun addTransaction(
        assetId: String,
        isBuy: Boolean,
        quantity: Double,
        price: Double,
        fee: Double,
        date: Long,
        note: String? = null
    ) {
        require(quantity > 0.0) { "Stueckzahl muss groesser als 0 sein" }
        require(price >= 0.0) { "Preis darf nicht negativ sein" }
        portfolioDao.insertTransaction(
            TransactionEntity(
                assetId = assetId,
                type = if (isBuy) TYPE_BUY else TYPE_SELL,
                quantity = quantity,
                price = price,
                fee = fee,
                date = date,
                note = note
            )
        )
        recalculateHolding(assetId)
    }

    suspend fun deleteTransaction(id: Long, assetId: String) {
        portfolioDao.deleteTransaction(id)
        recalculateHolding(assetId)
    }

    /**
     * Rechnet Stueckzahl und Einstand aus allen Transaktionen neu. Dadurch bleibt das
     * Depot auch nach dem Loeschen einer Buchung korrekt.
     */
    private suspend fun recalculateHolding(assetId: String) {
        val transactions = portfolioDao.transactionsForAsset(assetId)
        var quantity = 0.0
        var averagePrice = 0.0
        transactions.forEach { transaction ->
            if (transaction.type == TYPE_BUY) {
                val newQuantity = quantity + transaction.quantity
                if (newQuantity > 0.0) {
                    val cost = quantity * averagePrice +
                        transaction.quantity * transaction.price + transaction.fee
                    averagePrice = cost / newQuantity
                }
                quantity = newQuantity
            } else {
                // Verkaeufe aendern den Einstandskurs der Restposition nicht.
                quantity = (quantity - transaction.quantity).coerceAtLeast(0.0)
                if (quantity == 0.0) averagePrice = 0.0
            }
        }
        if (quantity <= 0.0) {
            portfolioDao.deleteHolding(assetId)
            return
        }
        val existing = portfolioDao.getHolding(assetId)
        portfolioDao.upsertHolding(
            HoldingEntity(
                id = existing?.id ?: 0,
                assetId = assetId,
                quantity = quantity,
                averagePrice = averagePrice,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    companion object {
        const val TYPE_BUY = "BUY"
        const val TYPE_SELL = "SELL"
    }
}
