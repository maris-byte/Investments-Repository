package com.marisbyte.invest.di

import android.content.Context
import com.marisbyte.invest.data.local.AppDatabase
import com.marisbyte.invest.data.remote.CoinGeckoApi
import com.marisbyte.invest.data.remote.CoinGeckoDataSource
import com.marisbyte.invest.data.remote.MarketDataProvider
import com.marisbyte.invest.data.remote.StooqApi
import com.marisbyte.invest.data.remote.StooqDataSource
import com.marisbyte.invest.data.remote.YahooApi
import com.marisbyte.invest.data.remote.YahooDataSource
import com.marisbyte.invest.data.repo.AnalysisRepository
import com.marisbyte.invest.data.repo.AnalysisRunner
import com.marisbyte.invest.data.repo.MarketRepository
import com.marisbyte.invest.data.repo.PortfolioRepository
import com.marisbyte.invest.data.repo.SettingsRepository
import com.marisbyte.invest.data.repo.UniverseSeeder
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Handverdrahtete Abhaengigkeiten. Die App ist klein genug, dass ein DI-Framework
 * mehr Bauzeit als Nutzen braechte.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // Yahoo und Stooq weisen Anfragen ohne User-Agent zurueck.
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/csv, text/plain, */*")
                .build()
            chain.proceed(request)
        }
        .build()

    private val jsonConverter = json.asConverterFactory("application/json".toMediaType())

    private fun retrofit(baseUrl: String, scalars: Boolean = false): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .apply { if (scalars) addConverterFactory(ScalarsConverterFactory.create()) }
            .addConverterFactory(jsonConverter)
            .build()

    private val database = AppDatabase.get(appContext)

    private val yahooApi: YahooApi =
        retrofit("https://query1.finance.yahoo.com/").create(YahooApi::class.java)
    private val stooqApi: StooqApi =
        retrofit("https://stooq.com/", scalars = true).create(StooqApi::class.java)
    private val coinGeckoApi: CoinGeckoApi =
        retrofit("https://api.coingecko.com/").create(CoinGeckoApi::class.java)

    private val marketDataProvider = MarketDataProvider(
        yahoo = YahooDataSource(yahooApi),
        stooq = StooqDataSource(stooqApi),
        coinGecko = CoinGeckoDataSource(coinGeckoApi)
    )

    val settingsRepository = SettingsRepository(appContext)

    val marketRepository = MarketRepository(
        assetDao = database.assetDao(),
        candleDao = database.candleDao(),
        provider = marketDataProvider
    )

    val analysisRepository = AnalysisRepository(
        analysisDao = database.analysisDao(),
        marketRepository = marketRepository,
        json = json
    )

    val portfolioRepository = PortfolioRepository(
        portfolioDao = database.portfolioDao(),
        assetDao = database.assetDao(),
        analysisDao = database.analysisDao()
    )

    val analysisRunner = AnalysisRunner(
        marketRepository = marketRepository,
        analysisRepository = analysisRepository,
        settingsRepository = settingsRepository
    )

    val universeSeeder = UniverseSeeder(appContext, database.assetDao(), json)

    val assetDao = database.assetDao()

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) InvestTracker/1.0 (Kursdaten fuer private Analyse)"
    }
}
