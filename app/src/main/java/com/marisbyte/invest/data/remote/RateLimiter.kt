package com.marisbyte.invest.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Haelt einen Mindestabstand zwischen zwei Anfragen an dieselbe Quelle ein.
 *
 * Beim ersten Lauf werden ueber 90 Instrumente nacheinander geladen; ohne Drosselung
 * antworten die kostenlosen Endpunkte mit HTTP 429, und der Lauf liefert nichts.
 */
class RateLimiter(private val minIntervalMillis: Long) {

    private val mutex = Mutex()
    private var lastCallAt = 0L

    suspend fun <T> withPermit(block: suspend () -> T): T {
        mutex.withLock {
            val waitFor = lastCallAt + minIntervalMillis - System.currentTimeMillis()
            if (waitFor > 0) delay(waitFor)
            lastCallAt = System.currentTimeMillis()
        }
        return block()
    }
}
