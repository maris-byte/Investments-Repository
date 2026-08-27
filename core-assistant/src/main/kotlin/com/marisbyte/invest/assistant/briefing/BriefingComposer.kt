package com.marisbyte.invest.assistant.briefing

import com.marisbyte.invest.assistant.model.Briefing
import com.marisbyte.invest.assistant.model.BriefingInput
import com.marisbyte.invest.assistant.model.BriefingSection
import com.marisbyte.invest.assistant.model.MarketBrief
import com.marisbyte.invest.assistant.model.RealEstateBrief
import com.marisbyte.invest.assistant.model.WeatherSnapshot
import com.marisbyte.invest.assistant.text.SpeechText
import com.marisbyte.invest.assistant.text.SpokenTime
import com.marisbyte.invest.assistant.weather.WeatherCodes
import java.time.ZoneId

/**
 * Baut den Bericht, den Alfred nach dem Weckwort vorliest: Begruessung, Wetter,
 * Maerkte, Immobilien, offene Aufgaben, Schlussfrage.
 *
 * Fehlende Bausteine werden stillschweigend weggelassen - lieber ein kurzer Bericht
 * als eine Aufzaehlung dessen, was gerade nicht geladen werden konnte. Nur wenn gar
 * nichts da ist, sagt Alfred es offen.
 */
object BriefingComposer {

    fun compose(input: BriefingInput, zone: ZoneId = ZoneId.systemDefault()): Briefing {
        val sections = buildList {
            add(BriefingSection("Begrüßung", greeting(input.userName, input.hourOfDay)))
            weather(input.weather)?.let { add(BriefingSection("Wetter", it)) }
            market(input.market)?.let { add(BriefingSection("Märkte", it)) }
            realEstate(input.realEstate)?.let { add(BriefingSection("Immobilien", it)) }
            tasks(input, zone)?.let { add(BriefingSection("Aufgaben", it)) }
            failures(input)?.let { add(BriefingSection("Hinweis", it)) }
            add(BriefingSection("Abschluss", "Was kann ich für dich tun?"))
        }
        return Briefing(sections)
    }

    fun greeting(name: String, hourOfDay: Int): String {
        val salutation = when (hourOfDay) {
            in 5..10 -> "Guten Morgen"
            in 11..17 -> "Guten Tag"
            in 18..22 -> "Guten Abend"
            else -> "Hallo"
        }
        val who = name.trim().ifBlank { "" }
        return if (who.isEmpty()) "$salutation." else "$salutation, $who."
    }

    fun weather(weather: WeatherSnapshot?): String? {
        if (weather == null) return null
        val parts = mutableListOf<String>()
        val day = if (weather.forTomorrow) "Morgen" else "Heute"
        val place = weather.locationName.trim()
        val description = WeatherCodes.describe(weather.weatherCode)

        val now = weather.temperatureNow
        if (!weather.forTomorrow && now != null) {
            val where = if (place.isEmpty()) "Draußen" else "In $place"
            parts += "$where sind es gerade ${SpeechText.temperature(now)}, $description"
        } else {
            val where = if (place.isEmpty()) "" else " in $place"
            parts += "$day wird es$where $description"
        }

        val min = weather.temperatureMin
        val max = weather.temperatureMax
        if (min != null && max != null) {
            parts += "$day zwischen ${SpeechText.temperature(min)} und ${SpeechText.temperature(max)}"
        } else if (max != null) {
            parts += "$day bis ${SpeechText.temperature(max)}"
        }

        val rain = weather.precipitationProbability
        if (rain != null && rain >= 20) {
            parts += "die Regenwahrscheinlichkeit liegt bei ${SpeechText.percent(rain.toDouble())}"
        }

        val advice = WeatherCodes.advice(
            weather.weatherCode,
            weather.precipitationProbability,
            weather.temperatureMax,
            weather.windSpeedMax
        )

        val sentence = SpeechText.capitalize(parts.joinToString(", ")) + "."
        return if (advice == null) sentence else "$sentence $advice"
    }

    fun market(market: MarketBrief?): String? {
        if (market == null || market.isEmpty) return null
        val parts = mutableListOf<String>()

        val value = market.portfolioValue
        val day = market.portfolioDayChangePercent
        if (value != null && value > 0.0) {
            val total = market.portfolioTotalProfitPercent
            val head = "Dein Depot steht bei ${SpeechText.money(value, market.currency)}"
            parts += when {
                day != null && total != null ->
                    "$head, ${SpeechText.changePercent(day)} seit gestern " +
                        "und insgesamt ${SpeechText.changePercent(total)}"
                day != null -> "$head, ${SpeechText.changePercent(day)} seit gestern"
                else -> head
            }
        }

        val best = market.gainers.firstOrNull()
        val worst = market.losers.firstOrNull()
        if (best != null && worst != null && best.symbol != worst.symbol) {
            parts += "Am stärksten läuft ${best.name} mit ${SpeechText.changePercent(best.changePercent)}, " +
                "am schwächsten ${worst.name} mit ${SpeechText.changePercent(worst.changePercent)}"
        } else if (best != null) {
            parts += "${best.name} ${SpeechText.changeVerb(best.changePercent)}"
        } else if (worst != null) {
            parts += "${worst.name} ${SpeechText.changeVerb(worst.changePercent)}"
        }

        val signal = market.topSignals.firstOrNull()
        if (signal?.score != null) {
            parts += "Das stärkste Signal hat ${signal.name} mit ${signal.score} von 100 Punkten"
        }

        if (parts.isEmpty()) return null
        return SpeechText.sentences(parts)
    }

    fun realEstate(realEstate: RealEstateBrief?): String? {
        if (realEstate == null || realEstate.isEmpty) return null
        val parts = mutableListOf<String>()

        val year = realEstate.changeYearPercent
        if (year != null) {
            val name = realEstate.indexName?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "Die Wohnimmobilienpreise"
            val period = realEstate.period?.let { " (Stand $it)" } ?: ""
            parts += "$name liegen ${SpeechText.changePercent(year)} gegenüber dem Vorjahr$period"
            realEstate.changeQuarterPercent?.let {
                parts += "gegenüber dem Vorquartal ${SpeechText.changePercent(it)}"
            }
        }

        val proxies = realEstate.marketProxies.take(2)
        if (proxies.isNotEmpty()) {
            val listed = proxies.joinToString(", ") {
                "${it.name} ${SpeechText.changePercent(it.changePercent)}"
            }
            parts += "Bei den Immobilienwerten an der Börse: $listed"
        }

        if (parts.isEmpty()) return null
        return SpeechText.sentences(parts)
    }

    private fun tasks(input: BriefingInput, zone: ZoneId): String? {
        val open = input.openTasks.filter { !it.done }
        if (open.isEmpty()) return null
        val count = SpeechText.plural(open.size, "offene Aufgabe", "offene Aufgaben")
        val next = open.filter { it.dueAt != null }.minByOrNull { it.dueAt!! }
            ?: open.first()
        val due = next.dueAt?.let {
            " Als Nächstes ${SpokenTime.dateTime(it, zone)}: ${next.text}."
        } ?: " Zum Beispiel: ${next.text}."
        return "Auf deiner Liste stehen $count.$due"
    }

    private fun failures(input: BriefingInput): String? {
        if (input.failures.isEmpty()) return null
        // Nur melden, wenn wirklich nichts geklappt hat - sonst stoert es den Bericht.
        val nothingLoaded = input.weather == null &&
            (input.market == null || input.market.isEmpty) &&
            (input.realEstate == null || input.realEstate.isEmpty)
        if (!nothingLoaded) return null
        return "Ich konnte gerade keine Daten laden: ${SpeechText.enumerate(input.failures)}."
    }
}
