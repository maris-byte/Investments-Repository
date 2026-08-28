# InvestTracker mit Alfred

Android-App zum Verfolgen der eigenen Investments **und** zur täglichen, kennzahlenbasierten
Bewertung von Aktien, ETFs, Edelmetallen, Rohstoffen und Kryptowährungen — mit **Alfred**,
dem Sprachassistenten, der auf seinen Namen hört, den Tag zusammenfasst und Aufgaben
entgegennimmt.

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

| Quelle | Rolle |
|--------|-------|
| Yahoo Finance (Chart-API) | Primärquelle für alles: Aktien, ETFs, Futures (Gold, Silber, Öl, Kupfer …) und Krypto – als einzige Quelle überall echte Tages-OHLC inklusive Volumen |
| Stooq (CSV) | Ausweichquelle für Aktien, ETFs und Futures |
| CoinGecko | Ausweichquelle für Kryptowährungen |

Fällt eine Quelle aus (Rate-Limit, Netzfehler, unbekanntes Symbol), übernimmt automatisch die
nächste. Ist gar nichts erreichbar, arbeitet die App mit dem lokalen Cache weiter. Jede Quelle
wird gedrosselt abgefragt, damit der erste Lauf über alle Instrumente nicht in ein Rate-Limit
läuft.

Zur CoinGecko-Einschränkung: der kostenlose OHLC-Endpunkt liefert über ein Jahr nur
4-Tages-Kerzen. Die App bildet deshalb Tageskerzen aus Kursen und Volumen des
market_chart-Endpunkts – die Tagesspanne ist dabei enger als real, weshalb Yahoo für Krypto
zuerst gefragt wird.


## Alfred, der Sprachassistent

Sag **„Alfred"** — er begrüßt dich mit Namen, liest das Wetter, die Lage an den Märkten und
die Entwicklung der Immobilienpreise vor und wartet danach auf deine Aufgaben.

```
„Alfred."
  → „Guten Morgen, Maris. In München sind es gerade 14 Grad, bedeckt, heute zwischen
     11 Grad und 21 Grad, die Regenwahrscheinlichkeit liegt bei 70 Prozent. Nimm einen
     Schirm mit. Dein Depot steht bei 12.500 Euro, plus 0,8 Prozent seit gestern und
     insgesamt plus 14,2 Prozent. Am stärksten läuft Siemens mit plus 2,1 Prozent, am
     schwächsten Bayer mit minus 1,4 Prozent. Die Wohnimmobilienpreise in Deutschland
     liegen plus 2,4 Prozent gegenüber dem Vorjahr. Was kann ich für dich tun?"
„Erinnere mich morgen um acht an den Zahnarzt."
  → „Notiert: Den Zahnarzt. Ich erinnere dich morgen um 8 Uhr."
„Such nach dem Zinsentscheid der EZB."
  → liest die Kurzantwort vor und nennt die Quelle
```

### Was er versteht

| Gesagt | Was passiert |
|--------|--------------|
| „Alfred" | Begrüßung mit Namen und der volle Tagesbericht |
| „Wie wird das Wetter morgen?", „Wetter in Hamburg" | Vorhersage für heute, morgen oder einen genannten Ort |
| „Wie steht mein Depot?", „Was macht Bitcoin?" | Depotlage bzw. ein einzelner Wert mit Kurs und Bewertung |
| „Was machen die Immobilienpreise?" | Preisindex und Immobilienwerte an der Börse |
| „Such nach …", „Was ist …?", jede offene Frage | Internetsuche, Antwort vorgelesen |
| „Merk dir …", „Erinnere mich morgen um acht an …" | Aufgabe mit oder ohne Termin, Erinnerung wird eingeplant |
| „Was steht heute an?", „Hake den Zahnarzt ab" | Aufgabenliste vorlesen bzw. erledigen |
| „Stell einen Timer auf zehn Minuten" | Timer mit Meldung |
| „Aktualisiere die Kurse" | startet die Analyse im Hintergrund |
| „Öffne das Depot" | wechselt den Bildschirm |
| „Wiederhole", „Was kannst du?", „Danke, das war alles" | Steuerung des Gesprächs |

Zeitangaben versteht er, wie man sie sagt: *in zehn Minuten*, *in einer halben Stunde*,
*morgen um halb sieben*, *heute Abend*, *übermorgen Mittag*.

### Wie das Weckwort funktioniert — und was das kostet

Android bietet **keine** Schnittstelle für ein eigenes Weckwort an. Alfred löst das ohne
fremde Bibliothek und ohne Schlüssel: ein Vordergrunddienst lässt die Spracherkennung des
Geräts im Kreis laufen und prüft jedes Zwischenergebnis auf den Namen. Weil die Erkennung
selten genau „Alfred" liefert, gelten auch *Alfredo*, *Alfret*, *Alfrid* und das getrennte
*Alf red* als Weckruf — *Manfred* und *Alfons* dagegen nicht.

Was daraus folgt, ehrlich gesagt:

- **Das kostet spürbar Akku.** Das Dauerlauschen ist deshalb standardmäßig **aus**, läuft nur
  als sichtbare Dauermeldung und lässt sich aus dieser Meldung heraus sofort beenden.
- Ab **Android 13** wird die Erkennung *auf dem Gerät* benutzt, wo sie verfügbar ist — dann
  verlässt kein gesprochenes Wort das Handy. Darunter läuft sie über den Dienst, den das
  Gerät für Spracheingaben eingerichtet hat.
- Manche Hersteller drosseln dauerhaft laufende Mikrofonzugriffe. Alfred fängt Abbrüche ab
  und startet die Erkennung mit wachsender Pause neu, aber garantieren lässt sich das nicht.
- **Ohne Dauerlauschen** geht es genauso: Alfred lässt sich in den Android-Einstellungen als
  Standard-Assistenz-App auswählen und antwortet dann auf die Assistenzgeste. Oder man tippt
  in der App unter **Alfred** auf *Bericht anhören*.

Das Gespräch selbst braucht keinen sichtbaren Bildschirm — es läuft im Dienst weiter, auch
wenn das Display aus ist.

### Datenquellen des Assistenten

Wie beim Rest der App: alles kostenlos, alles ohne API-Schlüssel.

| Quelle | Rolle |
|--------|-------|
| Open-Meteo | Wetter und Ortssuche |
| EZB-Datenportal (Datensatz RESR) | Wohnimmobilien-Preisindex, Quartalswerte |
| Vonovia, LEG, TAG, Aroundtown, Immobilien-ETF | tagesaktueller Indikator für den Immobilienmarkt |
| DuckDuckGo Instant Answer | Kurzantworten aus der Internetsuche |
| Wikipedia | Auffangnetz für alles, was DuckDuckGo nicht weiß |
| die App selbst | Depot, Kurse und Bewertungen aus der letzten Tagesanalyse |

Der Preisindex ist eine **Quartalsreihe** und liegt einige Monate zurück — deshalb der
zweite Blickwinkel über die börsengehandelten Immobilienwerte, die jeden Morgen eine frische
Zahl liefern. Der Reihenschlüssel der EZB-Zeitreihe steht in den Einstellungen und lässt sich
ändern, etwa auf den Euroraum oder ein anderes Land; fällt er aus, bleibt der Börsenteil
bestehen.

Die Internetsuche liefert Textantworten, keine Trefferliste. Für tagesaktuelle Ereignisse
ist das die Grenze des Verfahrens — dann sagt Alfred, dass er nichts Brauchbares gefunden hat.

### Berechtigungen

| Berechtigung | Wofür | Ohne sie |
|--------------|-------|----------|
| Mikrofon | zuhören | Alfred liest den Bericht vor, nimmt aber keine Befehle an |
| Benachrichtigungen | Dauermeldung, Erinnerungen, Timer | kein Dauerlauschen, keine Erinnerungsmeldung |
| Ungefährer Standort | Wetter am aktuellen Ort | es gilt der Ort aus den Einstellungen |
| Nach Neustart starten | Dauerlauschen und Erinnerungen wiederherstellen | beides muss von Hand neu gestartet werden |

Erinnerungen laufen über den WorkManager statt über einen exakten Wecker — dafür bräuchte die
App ab Android 12 eine gesonderte Systemberechtigung, die sie sonst nirgends benötigt. Der
Preis: im Energiesparmodus kann eine Meldung ein paar Minuten später kommen.

### Einstellungen

Im Reiter **Alfred**: Name, Weckwort, Ort fürs Wetter, welche Abschnitte im Bericht
vorkommen, Sprechtempo, der EZB-Reihenschlüssel — und die Aufgabenliste zum Nachlesen,
Abhaken und Löschen.

## Aufbau

```
core-analysis/     reines Kotlin/JVM-Modul: Indikatoren + Bewertungsmodell (unit-getestet)
core-assistant/    reines Kotlin/JVM-Modul: Alfreds Sprachlogik (unit-getestet)
  wake             Weckworterkennung inklusive der üblichen Hörfehler
  intent           deutscher Befehls- und Zeitparser
  briefing/reply   die gesprochenen Texte
  text/weather     Zahlwörter, Uhrzeiten, WMO-Wettercodes
  market/task      Zuordnung gesprochener Namen zu Instrumenten und Aufgaben
app/               Android-App (Jetpack Compose, Room, WorkManager, Retrofit)
  data/local       Room-Datenbank: Instrumente, Kerzen, Analysen, Depot, Aufgaben
  data/remote      Yahoo/Stooq/CoinGecko + Fallback-Kette
  data/repo        Repositories, Analyselauf, Depotlogik
  assistant        Alfred: Sitzung, Weckwort-Dienst, Sprachein-/-ausgabe, Datenquellen
  work             Tagesanalyse im Hintergrund + Benachrichtigungen
  ui               Compose-Oberfläche (Übersicht, Märkte, Detail, Depot, Alfred, Mehr)
```

Analyse und Sprachlogik liegen bewusst in eigenen Modulen ohne Android-Abhängigkeiten: Sie
sind damit auf jedem JVM-System testbar. Im Android-Teil bleiben nur die Schalen — Mikrofon,
Sprachausgabe, Netz, Datenbank, Oberfläche.

## Bauen

Voraussetzungen: Android Studio (Ladybug oder neuer) bzw. Android SDK 35, JDK 17.

```bash
./gradlew :core-analysis:test        # Tests des Bewertungsmodells
./gradlew :core-assistant:test       # Tests der Sprachlogik (Weckwort, Befehle, Texte)
./gradlew test                       # beides
./gradlew :app:assembleDebug         # Debug-APK bauen
./gradlew :app:installDebug          # auf angeschlossenem Gerät installieren
```

Die fertige APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk` und lässt sich
per USB oder Dateiübertragung auf dem Handy installieren (Installation aus unbekannten Quellen
muss dafür erlaubt sein). Minimum ist Android 8.0 (API 26).

## Erste Schritte in der App

0. Für Alfred: Reiter **Alfred** öffnen, Namen und Ort eintragen, *Bericht anhören* antippen.
   Wer ihn rufen statt antippen will, schaltet dort **„Auf Alfred hören"** ein und erlaubt
   den Zugriff aufs Mikrofon.
1. App starten – das Universum wird beim ersten Start eingespielt.
2. Oben rechts auf **Aktualisieren** tippen. Der erste Lauf lädt für alle Instrumente bis zu
   zwei Jahre Historie und dauert einige Minuten.
3. Modus wählen (Buy & Hold, Swingtrading, Daytrading) – die Rangliste ordnet sich sofort neu.
4. Unter **Einstellungen** die Uhrzeit der Tagesanalyse festlegen. Wer Datenvolumen sparen will,
   aktiviert *Nur Beobachtungsliste* und markiert interessante Werte mit dem Stern.
5. Käufe über das Plus-Symbol in der Detailansicht buchen – das Depot rechnet daraus alles Weitere.

## Grenzen

- Das Weckwort läuft über die normale Spracherkennung des Geräts, nicht über ein eigens
  trainiertes Modell: es kostet Akku, und in einem lauten Raum wird der Name nicht immer
  erkannt.
- Alfreds Befehlsverständnis ist regelbasiert, kein Sprachmodell. Er versteht die Sätze aus
  der Tabelle oben und deren übliche Abwandlungen — freies Plaudern nicht.
- Für Deutsch müssen Sprachausgabe und Spracherkennung auf dem Gerät eingerichtet sein; Alfred
  sagt Bescheid, wenn die Sprachdaten fehlen.
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
