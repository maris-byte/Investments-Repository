# InvestTracker

Android-App zum Verfolgen der eigenen Investments **und** zur täglichen, kennzahlenbasierten
Bewertung von Aktien, ETFs, Edelmetallen, Rohstoffen und Kryptowährungen.

Jedes Instrument bekommt einen Score von **1 bis 100**:

| Score | Bedeutung |
|-------|-----------|
| 1–19 | Extrem Strong Sell |
| 20–34 | Sell |
| 35–45 | Reduzieren |
| **46–54** | **Neutral / Seitwärts** |
| 55–65 | Akkumulieren |
| 66–79 | Buy |
| 80–100 | Extrem Strong Buy |

Welche Kennzahlen in den Score einfließen und wie stark, hängt vom gewählten **Modus** ab:

| Modus | Horizont | Schwerpunkt |
|-------|----------|-------------|
| **Buy & Hold** | Monate bis Jahre | Primärtrend, 12-1-Momentum, Trendqualität, Einstiegslage, Rendite je Risiko, Akkumulation |
| **Swingtrading** | Tage bis Wochen | EMA-Struktur, MACD, RSI, ADX/DI, Bollinger, Stochastik, Volumen |
| **Daytrading** | Stunden bis wenige Tage | EMA 9/21, Kurzfrist-Momentum, RSI(7), Volumenschub, Schlusskurslage, Gap, ATR |

Die vollständige Methodik steht in [docs/SCORING.md](docs/SCORING.md).

## Funktionen

- **Übersicht** – stärkste Kauf- und Verkaufssignale des Tages, Depot-Kurzfassung,
  Beobachtungsliste, Moduswechsel mit einem Tipp.
- **Märkte** – 91 vorkonfigurierte Instrumente, filterbar nach Anlageklasse, durchsuchbar,
  sortierbar nach Score, Tagesveränderung oder Name.
- **Detailansicht** – Score-Anzeige mit Konfidenz, jeder einzelne Bewertungsbaustein mit
  Messwert und Begründung, ATR-basierter Handelsplan (Einstieg, Stopp, zwei Ziele),
  Kennzahlen-Tabelle, Kursverlauf und der direkte Vergleich aller drei Strategien.
- **Depot** – Positionen aus Kauf-/Verkaufsbuchungen, Durchschnittseinstand, Gewinn/Verlust,
  Tagesveränderung, Aufteilung nach Anlageklasse und ein gewichteter Depot-Score.
- **Tagesanalyse** – läuft per WorkManager automatisch zur eingestellten Uhrzeit und meldet
  die stärksten Signale per Benachrichtigung.

## Datenquellen

Alle Quellen sind kostenlos und benötigen **keinen API-Schlüssel**:

| Quelle | Abdeckung |
|--------|-----------|
| Yahoo Finance (Chart-API) | Aktien, ETFs, Futures (Gold, Silber, Öl, Kupfer …), Krypto |
| Stooq (CSV) | Ausweichquelle für Aktien, ETFs und Futures |
| CoinGecko | Kryptowährungen (OHLC + Volumen) |

Fällt eine Quelle aus (Rate-Limit, Netzfehler, unbekanntes Symbol), übernimmt automatisch die
nächste. Ist gar nichts erreichbar, arbeitet die App mit dem lokalen Cache weiter.

## Aufbau

```
core-analysis/     reines Kotlin/JVM-Modul: Indikatoren + Bewertungsmodell (unit-getestet)
app/               Android-App (Jetpack Compose, Room, WorkManager, Retrofit)
  data/local       Room-Datenbank: Instrumente, Kerzen, Analysen, Depot
  data/remote      Yahoo/Stooq/CoinGecko + Fallback-Kette
  data/repo        Repositories, Analyselauf, Depotlogik
  work             Tagesanalyse im Hintergrund + Benachrichtigungen
  ui               Compose-Oberfläche (Übersicht, Märkte, Detail, Depot, Einstellungen)
```

Die Analyse liegt bewusst in einem eigenen Modul ohne Android-Abhängigkeiten: Sie ist damit
auf jedem JVM-System testbar und lässt sich später ohne Umbau wiederverwenden.

## Bauen

Voraussetzungen: Android Studio (Ladybug oder neuer) bzw. Android SDK 35, JDK 17.

```bash
./gradlew :core-analysis:test        # Tests des Bewertungsmodells
./gradlew :app:assembleDebug         # Debug-APK bauen
./gradlew :app:installDebug          # auf angeschlossenem Gerät installieren
```

Die fertige APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk` und lässt sich
per USB oder Dateiübertragung auf dem Handy installieren (Installation aus unbekannten Quellen
muss dafür erlaubt sein). Minimum ist Android 8.0 (API 26).

## Erste Schritte in der App

1. App starten – das Universum wird beim ersten Start eingespielt.
2. Oben rechts auf **Aktualisieren** tippen. Der erste Lauf lädt für alle Instrumente bis zu
   zwei Jahre Historie und dauert einige Minuten.
3. Modus wählen (Buy & Hold, Swingtrading, Daytrading) – die Rangliste ordnet sich sofort neu.
4. Unter **Einstellungen** die Uhrzeit der Tagesanalyse festlegen. Wer Datenvolumen sparen will,
   aktiviert *Nur Beobachtungsliste* und markiert interessante Werte mit dem Stern.
5. Käufe über das Plus-Symbol in der Detailansicht buchen – das Depot rechnet daraus alles Weitere.

## Grenzen

- Die Analyse arbeitet auf **Tageskerzen**. Der Daytrading-Modus bewertet damit die Ausgangslage
  für die nächste Sitzung; echte Intraday-Signale (1–15 Minuten) brauchen einen kostenpflichtigen
  Datenfeed.
- Bewertet wird ausschließlich der Kursverlauf (technische Analyse). Bilanzkennzahlen,
  Nachrichten und Termine fließen nicht ein.
- Die App rechnet Fremdwährungen nicht um: Positionen werden in der Währung des Instruments geführt.

## Hinweis

Alle Bewertungen sind eine automatisierte Auswertung historischer Kurse und ausdrücklich
**keine Anlageberatung**. Kursdaten stammen von kostenlosen Quellen und können fehlerhaft,
verzögert oder unvollständig sein.
