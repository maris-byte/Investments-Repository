package com.marisbyte.invest.assistant.weather

/**
 * Uebersetzung der WMO-Wettercodes (wie sie Open-Meteo liefert) in deutsche Saetze.
 * Codes ausserhalb der Tabelle werden grob nach Zehnergruppe eingeordnet, damit
 * Alfred auch bei einer Erweiterung des Katalogs etwas Sinnvolles sagt.
 */
object WeatherCodes {

    private val DESCRIPTIONS: Map<Int, String> = mapOf(
        0 to "klar",
        1 to "überwiegend klar",
        2 to "teils bewölkt",
        3 to "bedeckt",
        45 to "neblig",
        48 to "neblig mit Reifansatz",
        51 to "leichter Nieselregen",
        53 to "Nieselregen",
        55 to "starker Nieselregen",
        56 to "gefrierender Nieselregen",
        57 to "starker gefrierender Nieselregen",
        61 to "leichter Regen",
        63 to "Regen",
        65 to "starker Regen",
        66 to "gefrierender Regen",
        67 to "starker gefrierender Regen",
        71 to "leichter Schneefall",
        73 to "Schneefall",
        75 to "starker Schneefall",
        77 to "Schneegriesel",
        80 to "einzelne Regenschauer",
        81 to "Regenschauer",
        82 to "kräftige Regenschauer",
        85 to "leichte Schneeschauer",
        86 to "starke Schneeschauer",
        95 to "Gewitter",
        96 to "Gewitter mit Hagel",
        99 to "schweres Gewitter mit Hagel"
    )

    /** Beschreibung im Satz: "Heute wird es *überwiegend klar*." */
    fun describe(code: Int?): String {
        if (code == null) return "wechselhaft"
        DESCRIPTIONS[code]?.let { return it }
        return when (code) {
            in 40..49 -> "neblig"
            in 50..59 -> "nieselig"
            in 60..69 -> "regnerisch"
            in 70..79 -> "winterlich"
            in 80..84 -> "schauerartig"
            in 85..89 -> "winterlich mit Schauern"
            in 90..99 -> "gewittrig"
            else -> "wechselhaft"
        }
    }

    fun isRainy(code: Int?): Boolean = code != null &&
        (code in 51..67 || code in 80..82 || code in 95..99)

    fun isSnowy(code: Int?): Boolean = code != null && (code in 71..77 || code in 85..86)

    /**
     * Ein kurzer Rat zum Tag. Es gibt hoechstens einen - eine Liste guter Ratschlaege
     * am Morgen will niemand hoeren.
     */
    fun advice(
        code: Int?,
        precipitationProbability: Int?,
        temperatureMax: Double?,
        windSpeedMax: Double?
    ): String? = when {
        isSnowy(code) -> "Zieh dich warm an, es schneit."
        code != null && code >= 95 -> "Bei Gewitter lieber nicht draußen bleiben."
        isRainy(code) || (precipitationProbability ?: 0) >= 60 -> "Nimm einen Schirm mit."
        (windSpeedMax ?: 0.0) >= 50.0 -> "Es wird windig, achte auf lose Gegenstände."
        (temperatureMax ?: 0.0) >= 28.0 -> "Trink heute genug."
        (temperatureMax ?: 99.0) <= 0.0 -> "Es bleibt frostig, zieh dich warm an."
        code != null && code <= 1 && (temperatureMax ?: 0.0) >= 18.0 ->
            "Ein guter Tag für draußen."
        else -> null
    }
}
