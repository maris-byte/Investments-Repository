package com.marisbyte.invest.assistant.intent

import java.time.ZonedDateTime

/**
 * Liest Zeitangaben aus gesprochenen deutschen Saetzen: "in zehn Minuten",
 * "um halb acht", "morgen um 7 Uhr", "heute Abend".
 *
 * Rueckgabe ist immer auch der getroffene Textbereich, damit der Aufgabentext
 * anschliessend ohne die Zeitangabe gespeichert werden kann.
 */
object GermanTimeParser {

    data class Result(val due: DueTime, val range: IntRange)

    private val NUMBER_WORDS: Map<String, Int> = mapOf(
        "null" to 0, "ein" to 1, "eine" to 1, "einer" to 1, "einem" to 1, "eins" to 1,
        "zwei" to 2, "drei" to 3, "vier" to 4, "fünf" to 5, "fuenf" to 5, "sechs" to 6,
        "sieben" to 7, "acht" to 8, "neun" to 9, "zehn" to 10, "elf" to 11, "zwölf" to 12,
        "zwoelf" to 12, "dreizehn" to 13, "vierzehn" to 14, "fünfzehn" to 15,
        "fuenfzehn" to 15, "zwanzig" to 20, "dreißig" to 30, "dreissig" to 30,
        "vierzig" to 40, "fünfzig" to 50, "fuenfzig" to 50, "sechzig" to 60
    )

    private const val NUMBER_PATTERN =
        "\\d{1,4}|null|eine[rnms]?|eins|zwei|drei|vier|fünf|fuenf|sechs|sieben|acht|neun|zehn|" +
            "elf|zwölf|zwoelf|dreizehn|vierzehn|fünfzehn|fuenfzehn|zwanzig|dreißig|dreissig|" +
            "vierzig|fünfzig|fuenfzig|sechzig"

    /** "in einer halben Stunde" - eigener Fall, weil "halb" die Zahl ersetzt. */
    private val HALF_HOUR = Regex(
        "(?U)\\b(?:in|nach)\\s+(?:einer\\s+)?halben\\s+stunde\\b",
        RegexOption.IGNORE_CASE
    )

    /** "in 10 Minuten", "auf 5 Minuten", "für zwei Stunden". */
    private val DURATION = Regex(
        "(?U)\\b(?:in|auf|für|fuer|nach)\\s+($NUMBER_PATTERN)\\s*" +
            "(sekunden|sekunde|minuten|minute|min|stunden|stunde|tagen|tage|tag|wochen|woche)\\b",
        RegexOption.IGNORE_CASE
    )

    /** "um halb acht", auch mit vorangestelltem "morgen". */
    private val HALF_PAST = Regex(
        "(?U)\\b(heute|morgen|übermorgen|uebermorgen)?\\s*um\\s+halb\\s+($NUMBER_PATTERN)\\b",
        RegexOption.IGNORE_CASE
    )

    /** "um 7", "um 19:30", "morgen um 7 Uhr", "um 8 Uhr abends". */
    private val CLOCK = Regex(
        "(?U)\\b(heute|morgen|übermorgen|uebermorgen)?\\s*um\\s+(\\d{1,2})(?:[:.](\\d{2}))?\\s*" +
            "(?:uhr)?\\s*(morgens|vormittags|mittags|nachmittags|abends|nachts)?\\b",
        RegexOption.IGNORE_CASE
    )

    /** "morgen früh", "heute Abend", "morgen Mittag". */
    private val DAY_PART = Regex(
        "(?U)\\b(heute|morgen|übermorgen|uebermorgen)\\s+" +
            "(früh|frueh|morgens?|vormittag|mittag|nachmittag|abend|nacht)\\b",
        RegexOption.IGNORE_CASE
    )

    /** Nur "heute Abend" ohne Tagesangabe: "am Abend", "heute Nacht". */
    private val BARE_DAY_PART = Regex(
        "(?U)\\b(?:am|heute)\\s+(vormittag|mittag|nachmittag|abend)\\b",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String): Result? =
        parseHalfHour(text)
            ?: parseDuration(text)
            ?: parseHalfPast(text)
            ?: parseClock(text)
            ?: parseDayPart(text)
            ?: parseBareDayPart(text)

    private fun parseHalfHour(text: String): Result? =
        HALF_HOUR.find(text)?.let { Result(DueTime.Relative(1800), it.range) }

    private fun parseDuration(text: String): Result? {
        val match = DURATION.find(text) ?: return null
        val amount = number(match.groupValues[1]) ?: return null
        val unit = match.groupValues[2].lowercase()
        val seconds = when {
            unit.startsWith("sek") -> amount
            unit.startsWith("min") -> amount * 60
            unit.startsWith("stund") -> amount * 3600
            unit.startsWith("tag") -> amount * 86_400
            unit.startsWith("woch") -> amount * 604_800
            else -> return null
        }
        return Result(DueTime.Relative(seconds.toLong()), match.range)
    }

    private fun parseHalfPast(text: String): Result? {
        val match = HALF_PAST.find(text) ?: return null
        val target = number(match.groupValues[2]) ?: return null
        // "halb acht" ist 7:30 - die Stunde davor.
        val hour = ((target - 1) + 24) % 24
        return Result(
            DueTime.AtClock(hour, 30, dayOffset(match.groupValues[1])),
            match.range
        )
    }

    private fun parseClock(text: String): Result? {
        val match = CLOCK.find(text) ?: return null
        val rawHour = match.groupValues[2].toIntOrNull() ?: return null
        if (rawHour > 24) return null
        val minute = match.groupValues[3].toIntOrNull() ?: 0
        if (minute > 59) return null
        val hour = applyDayPart(rawHour, match.groupValues[4])
        return Result(
            DueTime.AtClock(hour % 24, minute, dayOffset(match.groupValues[1])),
            match.range
        )
    }

    private fun parseDayPart(text: String): Result? {
        val match = DAY_PART.find(text) ?: return null
        val hour = dayPartHour(match.groupValues[2]) ?: return null
        return Result(
            DueTime.AtClock(hour, 0, dayOffset(match.groupValues[1])),
            match.range
        )
    }

    private fun parseBareDayPart(text: String): Result? {
        val match = BARE_DAY_PART.find(text) ?: return null
        val hour = dayPartHour(match.groupValues[1]) ?: return null
        return Result(DueTime.AtClock(hour, 0, 0), match.range)
    }

    /** "8 Uhr abends" ist 20 Uhr, "7 Uhr morgens" bleibt 7 Uhr. */
    private fun applyDayPart(hour: Int, part: String): Int {
        if (part.isEmpty() || hour >= 13) return hour
        return when (part.lowercase()) {
            "nachmittags" -> hour + 12
            "abends" -> if (hour <= 11) hour + 12 else hour
            "nachts" -> if (hour in 1..11) hour else hour
            "mittags" -> if (hour < 12) 12 else hour
            else -> hour
        }
    }

    private fun dayPartHour(part: String): Int? = when (part.lowercase()) {
        "früh", "frueh", "morgen", "morgens" -> 7
        "vormittag" -> 10
        "mittag" -> 12
        "nachmittag" -> 15
        "abend" -> 19
        "nacht" -> 22
        else -> null
    }

    private fun dayOffset(day: String): Int = when (day.lowercase()) {
        "morgen" -> 1
        "übermorgen", "uebermorgen" -> 2
        else -> 0
    }

    private fun number(raw: String): Int? =
        raw.toIntOrNull() ?: NUMBER_WORDS[raw.lowercase()]
}

/** Rechnet eine [DueTime] gegen die aktuelle Zeit in einen konkreten Zeitpunkt um. */
object DueTimeResolver {

    fun resolve(due: DueTime, now: ZonedDateTime): ZonedDateTime = when (due) {
        is DueTime.Relative -> now.plusSeconds(due.seconds)
        is DueTime.AtClock -> {
            val candidate = now
                .withHour(due.hour.coerceIn(0, 23))
                .withMinute(due.minute.coerceIn(0, 59))
                .withSecond(0)
                .withNano(0)
                .plusDays(due.dayOffset.toLong())
            // Ohne genannten Tag meint "um sieben" den naechsten Sieben-Uhr-Zeitpunkt.
            if (due.dayOffset == 0 && !candidate.isAfter(now)) candidate.plusDays(1) else candidate
        }
    }

    fun resolveMillis(due: DueTime, now: ZonedDateTime): Long =
        resolve(due, now).toInstant().toEpochMilli()
}
