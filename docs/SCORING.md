# Bewertungsmodell

Dieses Dokument beschreibt, wie aus einer Kurshistorie ein Score von 1 bis 100 wird.
Der Code dazu liegt vollständig im Modul `core-analysis`.

## Ablauf

```
Tageskerzen (OHLCV)
      │
      ▼
MarketContext ......... berechnet einmalig alle Kennzahlen
      │
      ▼
Strategie-Scorer ...... jeder Baustein wird auf 0..100 normiert (50 = neutral)
      │
      ├── Richtungsbausteine  → gewichteter Mittelwert = Rohscore
      └── Qualitätsfilter     → multiplikativ auf die Auslenkung um 50
      │
      ▼
Score 1..100 + Rating + Konfidenz + Handelsplan
```

### Warum zwei Arten von Bausteinen?

Ein Baustein wie „genug Tagesbewegung für einen Trade" (ATR) beantwortet nicht die Frage
*kaufen oder verkaufen*, sondern *überhaupt handelbar*. Würde er wie ein Richtungssignal
gewichtet, könnte ein ruhiger Abwärtstrend allein durch eine „gesunde" ATR Richtung 50
gezogen werden – oder ein Seitwärtsmarkt zum Kauf hochgewertet.

Deshalb gilt:

- **Richtungsbausteine** ergeben den Rohscore (gewichteter Mittelwert, Gewichte summieren zu 1).
- **Qualitätsfilter** wirken nur auf den *Abstand zu 50*: 0 Punkte dämpfen ihn auf 75 %,
  100 Punkte verstärken ihn auf 125 %. Ein Filter kann ein Signal abschwächen,
  aber aus einem neutralen Bild niemals ein Kaufsignal machen.

Zum Schluss wird die Auslenkung um 50 mit dem Faktor 1,10 gespreizt, damit die Skala
tatsächlich ausgenutzt wird, und auf 1..100 begrenzt.

## Normierungsfunktionen

| Funktion | Verwendung |
|----------|------------|
| `linear(wert, worst, best)` | lineare Abbildung mit harten Grenzen |
| `soft(wert, neutral, scale)` | tanh-Abbildung, robust gegen Ausreißer – Standard für Renditen |
| `band(wert, hardLow, idealLow, idealHigh, hardHigh)` | Zielkorridor mit linearem Abfall nach außen |

## Buy & Hold

Mindesthistorie 220 Handelstage, volle Konfidenz ab 380.

| Baustein | Gewicht | Kennzahl | Logik |
|----------|---------|----------|-------|
| Primärtrend | 24 % | Kurs vs. SMA 200, Steigung der SMA 200 | Kurs über steigender 200-Tage-Linie = intakter Primärtrend |
| 12-1-Momentum | 18 % | Rendite 12 Monate ohne den letzten Monat | Klassischer Momentum-Faktor; der ausgelassene Monat filtert die kurzfristige Umkehrtendenz |
| 6-Monats-Momentum | 10 % | ROC(126) | Bestätigt oder widerlegt das Jahresmomentum |
| Trendqualität | 12 % | R² der Log-Regression, Anteil positiver Wochen | Ruhiger Trend statt Zufallspfad |
| Einstiegslage | 12 % | Abstand zum 52-Wochen-Hoch | Rücksetzer von 2–12 % im Aufwärtstrend sind gute Einstiege; im Abwärtstrend bleibt der Baustein unter 50 und wird zusätzlich mit der Trendstärke skaliert |
| Rendite je Risiko | 12 % | Trendrendite / Volatilität, MaxDD / Volatilität | Sharpe-Proxy; niedrige Volatilität allein ist kein Kaufgrund |
| Akkumulation | 12 % | OBV-Trend über 21 Tage | Trägt Volumen die Bewegung? |

## Swingtrading

Mindesthistorie 70 Handelstage, volle Konfidenz ab 180.

| Baustein | Gewicht | Kennzahl |
|----------|---------|----------|
| MACD-Dynamik | 19 % | Histogramm, Richtung, Frische des Kreuzes |
| EMA-Struktur | 18 % | EMA 20 vs. EMA 50, Kurs vs. EMA 20 |
| RSI (14) | 16 % | Zielzone 48–65 mit steigender Tendenz; überverkauft im Aufwärtstrend gibt Bonus |
| Trendstärke | 14 % | ADX plus Richtung aus +DI/−DI |
| Bollinger-Position | 13 % | %B, Squeeze-Erkennung in Richtung der Struktur |
| Stochastik | 11 % | %K als Richtungswert, Extremzonen gedämpft, %K/%D-Kreuz |
| Volumenbestätigung | 11 % | Tagesvolumen vs. Ø20, in Richtung des Tages |
| *Bewegungsspielraum (ATR)* | Filter | Ziel 1,2–4 % Tagesrange |

## Daytrading

Mindesthistorie 40 Handelstage, volle Konfidenz ab 90.

| Baustein | Gewicht | Kennzahl |
|----------|---------|----------|
| Kurzfrist-Trend | 23 % | EMA 9 vs. EMA 21, Kurs vs. EMA 9 |
| Kurzfrist-Momentum | 20 % | ROC(3) und ROC(5) |
| RSI (7) | 16 % | Richtungswert, über 85 bzw. unter 15 gedeckelt |
| Volumenschub | 16 % | Tagesvolumen vs. Ø20 |
| Schlusskurslage | 14 % | Position des Schlusskurses in der Tagesrange |
| Gap-Verhalten | 11 % | Eröffnungslücke, gehalten oder geschlossen |
| *Tagesspielraum (ATR)* | Filter | Ziel 1,5–6 % Tagesrange |

## Konfidenz

Der Konfidenzwert (1..100) sagt, wie belastbar der Score ist:

- **40 %** Länge der Historie (zwischen Mindest- und Idealwert der Strategie)
- **40 %** Einigkeit der Richtungsbausteine (geringe Streuung = hohe Konfidenz)
- **20 %** Datenqualität (liegen Volumendaten vor?)

Ein Score von 78 bei Konfidenz 40 bedeutet: die Bausteine widersprechen sich deutlich
oder die Historie ist kurz – das Signal ist mit Vorsicht zu genießen.

## Handelsplan

Der Stopp liegt ein Vielfaches der ATR(14) vom letzten Schlusskurs entfernt, damit normales
Marktrauschen ihn nicht auslöst. Die Ziele sind Vielfache dieses Risikos (R):

| Modus | Stopp | Ziel 1 | Ziel 2 |
|-------|-------|--------|--------|
| Daytrading | 1,0 × ATR | 1,5 R | 2,5 R |
| Swingtrading | 1,8 × ATR | 2,0 R | 3,5 R |
| Buy & Hold | 3,5 × ATR | 2,5 R | 5,0 R |

Bei einem Score unter 50 wird der Plan gespiegelt (Short-Sicht).

## Anlageklassen

Die Klasse beeinflusst die Normierung: Krypto handelt 365 statt 252 Tage im Jahr, und die
erwartete Jahresvolatilität ist die Messlatte für den Risikobaustein.

| Klasse | Handelstage | erwartete Volatilität p.a. |
|--------|-------------|----------------------------|
| Aktien | 252 | 28 % |
| ETFs & Indizes | 252 | 18 % |
| Edelmetalle | 252 | 18 % |
| Rohstoffe | 252 | 30 % |
| Kryptowährungen | 365 | 65 % |

Dadurch wird ein Bitcoin-Rückgang von 20 % nicht wie ein 20-%-Einbruch bei einem Anleihen-ETF
bewertet.

## Was das Modell nicht kann

- Keine Fundamentaldaten (KGV, Verschuldung, Gewinnwachstum), keine Nachrichten, keine Termine.
- Keine Intraday-Daten – der Daytrading-Modus bewertet die Ausgangslage, nicht den Verlauf
  innerhalb des Tages.
- Keine Backtests: die App bewertet den aktuellen Zustand, sie misst nicht die historische
  Trefferquote der eigenen Signale.
