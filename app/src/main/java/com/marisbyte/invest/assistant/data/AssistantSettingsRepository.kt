package com.marisbyte.invest.assistant.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Eigener Speicher, damit die Einstellungen der Analyse unberuehrt bleiben. */
private val Context.alfredDataStore by preferencesDataStore(name = "alfred")

/** Alles, was Alfred ueber Maris und ueber sich selbst wissen muss. */
class AssistantSettingsRepository(private val context: Context) {

    data class Settings(
        val userName: String = DEFAULT_USER_NAME,
        val wakeWord: String = DEFAULT_WAKE_WORD,
        /** Lauscht Alfred dauerhaft auf sein Weckwort? */
        val wakeWordEnabled: Boolean = false,
        /** Ort fuer das Wetter. Leer bedeutet: Position des Geraets verwenden. */
        val weatherCity: String = "",
        val briefingWeather: Boolean = true,
        val briefingMarket: Boolean = true,
        val briefingRealEstate: Boolean = true,
        /** Reihenschluessel des Immobilienpreisindex im EZB-Datenportal. */
        val realEstateSeriesKey: String = RealEstateRepository.DEFAULT_SERIES_KEY,
        /** Sprechtempo der Sprachausgabe, 1.0 ist die Normalgeschwindigkeit. */
        val speechRate: Float = 1.0f
    )

    val settings: Flow<Settings> = context.alfredDataStore.data.map { prefs ->
        Settings(
            userName = prefs[KEY_NAME] ?: DEFAULT_USER_NAME,
            wakeWord = prefs[KEY_WAKE_WORD]?.takeIf { it.isNotBlank() } ?: DEFAULT_WAKE_WORD,
            wakeWordEnabled = prefs[KEY_WAKE_ENABLED] ?: false,
            weatherCity = prefs[KEY_CITY] ?: "",
            briefingWeather = prefs[KEY_BRIEF_WEATHER] ?: true,
            briefingMarket = prefs[KEY_BRIEF_MARKET] ?: true,
            briefingRealEstate = prefs[KEY_BRIEF_REAL_ESTATE] ?: true,
            realEstateSeriesKey = prefs[KEY_SERIES]?.takeIf { it.isNotBlank() }
                ?: RealEstateRepository.DEFAULT_SERIES_KEY,
            speechRate = prefs[KEY_SPEECH_RATE] ?: 1.0f
        )
    }

    suspend fun setUserName(name: String) = edit { it[KEY_NAME] = name.trim() }

    suspend fun setWakeWord(word: String) = edit { it[KEY_WAKE_WORD] = word.trim() }

    suspend fun setWakeWordEnabled(enabled: Boolean) = edit { it[KEY_WAKE_ENABLED] = enabled }

    suspend fun setWeatherCity(city: String) = edit { it[KEY_CITY] = city.trim() }

    suspend fun setBriefingWeather(enabled: Boolean) = edit { it[KEY_BRIEF_WEATHER] = enabled }

    suspend fun setBriefingMarket(enabled: Boolean) = edit { it[KEY_BRIEF_MARKET] = enabled }

    suspend fun setBriefingRealEstate(enabled: Boolean) =
        edit { it[KEY_BRIEF_REAL_ESTATE] = enabled }

    suspend fun setRealEstateSeriesKey(key: String) = edit { it[KEY_SERIES] = key.trim() }

    suspend fun setSpeechRate(rate: Float) =
        edit { it[KEY_SPEECH_RATE] = rate.coerceIn(0.5f, 2.0f) }

    private suspend fun edit(
        block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit
    ) {
        context.alfredDataStore.edit { preferences -> block(preferences) }
    }

    companion object {
        const val DEFAULT_USER_NAME = "Maris"
        const val DEFAULT_WAKE_WORD = "Alfred"

        private val KEY_NAME = stringPreferencesKey("user_name")
        private val KEY_WAKE_WORD = stringPreferencesKey("wake_word")
        private val KEY_WAKE_ENABLED = booleanPreferencesKey("wake_word_enabled")
        private val KEY_CITY = stringPreferencesKey("weather_city")
        private val KEY_BRIEF_WEATHER = booleanPreferencesKey("briefing_weather")
        private val KEY_BRIEF_MARKET = booleanPreferencesKey("briefing_market")
        private val KEY_BRIEF_REAL_ESTATE = booleanPreferencesKey("briefing_real_estate")
        private val KEY_SERIES = stringPreferencesKey("real_estate_series")
        private val KEY_SPEECH_RATE = floatPreferencesKey("speech_rate")
    }
}
