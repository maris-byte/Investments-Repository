package com.marisbyte.invest.assistant.text

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** Zeitpunkte so ausdruecken, wie man sie sagt: "morgen um 7 Uhr", "am Freitag um 9:30 Uhr". */
object SpokenTime {

    private val DE = Locale.GERMANY

    fun clock(time: ZonedDateTime): String {
        val minute = time.minute
        return if (minute == 0) "${time.hour} Uhr" else "${time.hour} Uhr ${pad(minute)}"
    }

    /** "heute um 9 Uhr", "morgen um 7 Uhr", "am Freitag um 9 Uhr", "am 3. September um 9 Uhr". */
    fun dateTime(target: ZonedDateTime, now: ZonedDateTime): String {
        val days = daysBetween(now.toLocalDate(), target.toLocalDate())
        val day = when {
            days == 0L -> "heute"
            days == 1L -> "morgen"
            days == 2L -> "übermorgen"
            days in 3..6 -> "am ${weekday(target)}"
            else -> "am ${target.dayOfMonth}. ${month(target)}"
        }
        return "$day um ${clock(target)}"
    }

    fun dateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTime(atZone(epochMillis, zone), ZonedDateTime.now(zone))

    /** "in 10 Minuten", "in 1 Stunde 30 Minuten" - fuer bestaetigte Timer. */
    fun duration(seconds: Long): String {
        if (seconds < 60) return SpeechText.plural(seconds.toInt(), "Sekunde", "Sekunden")
        val minutes = seconds / 60
        if (minutes < 60) return SpeechText.plural(minutes.toInt(), "Minute", "Minuten")
        val hours = minutes / 60
        val restMinutes = minutes % 60
        val hourPart = SpeechText.plural(hours.toInt(), "Stunde", "Stunden")
        if (restMinutes == 0L) return hourPart
        return "$hourPart und ${SpeechText.plural(restMinutes.toInt(), "Minute", "Minuten")}"
    }

    fun weekday(time: ZonedDateTime): String =
        time.dayOfWeek.getDisplayName(TextStyle.FULL, DE)

    fun month(time: ZonedDateTime): String =
        time.month.getDisplayName(TextStyle.FULL, DE)

    /** "Mittwoch, der 27. August 2026" - Antwort auf "Welcher Tag ist heute?". */
    fun fullDate(time: ZonedDateTime): String =
        "${weekday(time)}, der ${time.dayOfMonth}. ${month(time)} ${time.year}"

    fun atZone(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(zone)

    /** Aus "2026-08-27T06:12" der Sonnenaufgangszeit wird "6:12 Uhr". */
    fun clockFromIso(isoTime: String?): String? {
        if (isoTime.isNullOrBlank()) return null
        val timePart = isoTime.substringAfter('T', "")
        val hour = timePart.substringBefore(':').toIntOrNull() ?: return null
        val minute = timePart.substringAfter(':', "").take(2).toIntOrNull() ?: return null
        return "$hour Uhr ${pad(minute)}"
    }

    private fun daysBetween(from: LocalDate, to: LocalDate): Long = to.toEpochDay() - from.toEpochDay()

    private fun pad(value: Int): String = value.toString().padStart(2, '0')
}
