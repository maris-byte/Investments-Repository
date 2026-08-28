package com.marisbyte.invest.assistant

import com.marisbyte.invest.assistant.data.AssistantMarketProvider
import com.marisbyte.invest.assistant.data.AssistantSettingsRepository
import com.marisbyte.invest.assistant.data.AssistantTaskRepository
import com.marisbyte.invest.assistant.data.RealEstateRepository
import com.marisbyte.invest.assistant.data.WeatherRepository
import com.marisbyte.invest.assistant.briefing.BriefingComposer
import com.marisbyte.invest.assistant.model.Briefing
import com.marisbyte.invest.assistant.model.BriefingInput
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.ZonedDateTime

/**
 * Sammelt alles fuer den Morgenbericht ein - Wetter, Maerkte, Immobilien, Aufgaben -
 * und laesst daraus den Text bauen.
 *
 * Die drei Netzabfragen laufen nebeneinander: der Bericht ist damit so schnell fertig
 * wie die langsamste Quelle, nicht wie ihre Summe. Was ausfaellt, wird vermerkt und
 * im Bericht weggelassen.
 */
class BriefingProvider(
    private val settingsRepository: AssistantSettingsRepository,
    private val weatherRepository: WeatherRepository,
    private val realEstateRepository: RealEstateRepository,
    private val marketProvider: AssistantMarketProvider,
    private val taskRepository: AssistantTaskRepository
) {

    suspend fun briefing(
        settings: AssistantSettingsRepository.Settings,
        now: ZonedDateTime = ZonedDateTime.now(),
        forceRefresh: Boolean = false
    ): Briefing = coroutineScope {
        val weather = async {
            if (!settings.briefingWeather) null
            else runCatching {
                weatherRepository.load(settings.weatherCity, forceRefresh = forceRefresh)
            }.getOrNull()
        }
        val market = async {
            if (!settings.briefingMarket) null
            else runCatching { marketProvider.marketBrief() }.getOrNull()
        }
        val realEstate = async {
            if (!settings.briefingRealEstate) null
            else runCatching {
                realEstateRepository.load(settings.realEstateSeriesKey, forceRefresh)
            }.getOrNull()
        }
        val tasks = runCatching { taskRepository.openTasks() }.getOrDefault(emptyList())

        val weatherResult = weather.await()
        val marketResult = market.await()
        val realEstateResult = realEstate.await()

        val failures = buildList {
            if (settings.briefingWeather && weatherResult == null) add("das Wetter")
            if (settings.briefingMarket && (marketResult == null || marketResult.isEmpty)) {
                add("die Kurse")
            }
            if (settings.briefingRealEstate &&
                (realEstateResult == null || realEstateResult.isEmpty)
            ) {
                add("die Immobilienpreise")
            }
        }

        BriefingComposer.compose(
            BriefingInput(
                userName = settings.userName,
                hourOfDay = now.hour,
                weather = weatherResult,
                market = marketResult,
                realEstate = realEstateResult,
                openTasks = tasks,
                failures = failures
            ),
            zone = now.zone
        )
    }
}
