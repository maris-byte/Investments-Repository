package com.marisbyte.invest.data.repo

import android.content.Context
import com.marisbyte.invest.data.local.AssetDao
import com.marisbyte.invest.data.local.AssetEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Liest das mitgelieferte Universum aus assets/universe.json in die Datenbank. */
class UniverseSeeder(
    private val context: Context,
    private val assetDao: AssetDao,
    private val json: Json
) {

    @Serializable
    private data class Universe(val version: Int = 1, val assets: List<UniverseAsset> = emptyList())

    @Serializable
    private data class UniverseAsset(
        val symbol: String,
        val name: String,
        val assetClass: String,
        val currency: String,
        val yahooSymbol: String,
        val stooqSymbol: String? = null,
        val coingeckoId: String? = null,
        val region: String = "",
        @SerialName("tags") val tags: List<String> = emptyList()
    )

    /** Fuegt fehlende Instrumente hinzu; bestehende Eintraege bleiben unangetastet. */
    suspend fun seedIfNeeded(): Int {
        val raw = context.assets.open(FILE_NAME).bufferedReader().use { it.readText() }
        val universe = json.decodeFromString(Universe.serializer(), raw)
        val entities = universe.assets.map { asset ->
            AssetEntity(
                id = "${asset.assetClass}:${asset.symbol}",
                symbol = asset.symbol,
                name = asset.name,
                assetClass = asset.assetClass,
                currency = asset.currency,
                yahooSymbol = asset.yahooSymbol,
                stooqSymbol = asset.stooqSymbol,
                coingeckoId = asset.coingeckoId,
                region = asset.region,
                isWatched = false
            )
        }
        assetDao.insertAll(entities)
        return entities.size
    }

    private companion object {
        const val FILE_NAME = "universe.json"
    }
}
