package com.marisbyte.invest.di

import android.content.Context
import com.marisbyte.invest.assistant.AlfredSession
import com.marisbyte.invest.assistant.BriefingProvider
import com.marisbyte.invest.assistant.CommandExecutor
import com.marisbyte.invest.assistant.data.AssistantMarketProvider
import com.marisbyte.invest.assistant.data.AssistantSettingsRepository
import com.marisbyte.invest.assistant.data.AssistantTaskRepository
import com.marisbyte.invest.assistant.data.DuckDuckGoApi
import com.marisbyte.invest.assistant.data.EcbApi
import com.marisbyte.invest.assistant.data.OpenMeteoApi
import com.marisbyte.invest.assistant.data.OpenMeteoGeocodingApi
import com.marisbyte.invest.assistant.data.RealEstateRepository
import com.marisbyte.invest.assistant.data.SearchRepository
import com.marisbyte.invest.assistant.data.WeatherRepository
import com.marisbyte.invest.assistant.data.WikipediaApi
import com.marisbyte.invest.assistant.speech.Speaker
import com.marisbyte.invest.assistant.speech.VoiceListener
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    // --- Alfred, der Sprachassistent ---------------------------------------------

    /** Laeuft laenger als ein Bildschirm: Analyselaeufe, die Alfred nur anstoesst. */
    private val assistantScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val openMeteoApi: OpenMeteoApi =
        retrofit("https://api.open-meteo.com/").create(OpenMeteoApi::class.java)
    private val geocodingApi: OpenMeteoGeocodingApi =
        retrofit("https://geocoding-api.open-meteo.com/").create(OpenMeteoGeocodingApi::class.java)
    private val ecbApi: EcbApi =
        retrofit("https://data-api.ecb.europa.eu/", scalars = true).create(EcbApi::class.java)
    private val duckDuckGoApi: DuckDuckGoApi =
        retrofit("https://api.duckduckgo.com/").create(DuckDuckGoApi::class.java)
    private val wikipediaApi: WikipediaApi =
        retrofit("https://de.wikipedia.org/").create(WikipediaApi::class.java)

    val assistantSettingsRepository = AssistantSettingsRepository(appContext)

    val assistantTaskRepository = AssistantTaskRepository(database.assistantTaskDao())

    private val weatherRepository = WeatherRepository(appContext, openMeteoApi, geocodingApi)

    private val realEstateRepository = RealEstateRepository(
        api = ecbApi,
        assetDao = database.assetDao(),
        marketRepository = marketRepository
    )

    private val searchRepository = SearchRepository(duckDuckGoApi, wikipediaApi)

    private val assistantMarketProvider = AssistantMarketProvider(
        settingsRepository = settingsRepository,
        portfolioRepository = portfolioRepository,
        marketRepository = marketRepository,
        analysisDao = database.analysisDao(),
        assetDao = database.assetDao()
    )

    private val briefingProvider = BriefingProvider(
        settingsRepository = assistantSettingsRepository,
        weatherRepository = weatherRepository,
        realEstateRepository = realEstateRepository,
        marketProvider = assistantMarketProvider,
        taskRepository = assistantTaskRepository
    )

    private val commandExecutor = CommandExecutor(
        context = appContext,
        settingsRepository = assistantSettingsRepository,
        weatherRepository = weatherRepository,
        realEstateRepository = realEstateRepository,
        marketProvider = assistantMarketProvider,
        searchRepository = searchRepository,
        taskRepository = assistantTaskRepository,
        analysisRunner = analysisRunner,
        backgroundScope = assistantScope
    )

    /**
     * Eine einzige Sitzung fuer die ganze App: Weckwort-Dienst und Bildschirm reden
     * mit demselben Alfred und sehen denselben Gespraechsverlauf.
     */
    val alfredSession = AlfredSession(
        speaker = Speaker(appContext),
        voiceListener = VoiceListener(appContext),
        settingsRepository = assistantSettingsRepository,
        briefingProvider = briefingProvider,
        commandExecutor = commandExecutor,
        scope = assistantScope
    )

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) InvestTracker/1.0 (Kursdaten fuer private Analyse)"
    }
}
