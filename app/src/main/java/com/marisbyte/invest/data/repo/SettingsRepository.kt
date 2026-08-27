package com.marisbyte.invest.data.repo

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.marisbyte.invest.analysis.model.Strategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Benutzereinstellungen: gewaehlte Strategie, Analysezeitpunkt, Benachrichtigungen. */
class SettingsRepository(private val context: Context) {

    data class Settings(
        val strategy: Strategy = Strategy.BUY_AND_HOLD,
        val analysisHour: Int = 7,
        val notificationsEnabled: Boolean = true,
        /** Nur beobachtete Werte analysieren spart Datenvolumen und Zeit. */
        val watchlistOnly: Boolean = false,
        val displayCurrency: String = "EUR"
    )

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            strategy = Strategy.fromKey(prefs[KEY_STRATEGY]),
            analysisHour = prefs[KEY_HOUR] ?: 7,
            notificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: true,
            watchlistOnly = prefs[KEY_WATCHLIST_ONLY] ?: false,
            displayCurrency = prefs[KEY_CURRENCY] ?: "EUR"
        )
    }

    suspend fun setStrategy(strategy: Strategy) {
        context.dataStore.edit { it[KEY_STRATEGY] = strategy.name }
    }

    suspend fun setAnalysisHour(hour: Int) {
        context.dataStore.edit { it[KEY_HOUR] = hour.coerceIn(0, 23) }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
    }

    suspend fun setWatchlistOnly(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WATCHLIST_ONLY] = enabled }
    }

    suspend fun setDisplayCurrency(currency: String) {
        context.dataStore.edit { it[KEY_CURRENCY] = currency }
    }

    private companion object {
        val KEY_STRATEGY = stringPreferencesKey("strategy")
        val KEY_HOUR = intPreferencesKey("analysis_hour")
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications")
        val KEY_WATCHLIST_ONLY = booleanPreferencesKey("watchlist_only")
        val KEY_CURRENCY = stringPreferencesKey("currency")
    }
}
