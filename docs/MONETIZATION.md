# Birdy: Monetarisierung

## Anzeigen

- Android: AdMob-App-ID `ca-app-pub-1786159152036324~5284705514`.
- Zwei freiwillige Rewarded-Ad-Blöcke: 30 Münzen (`ca-app-pub-1786159152036324/7854280106`) oder ein einstündiger Style-Pass (`ca-app-pub-1786159152036324/8020434087`).
- Insgesamt höchstens drei belohnte Anzeigen pro lokalem Tag. Der Style-Pass gibt vorübergehend Zugriff auf alle vorhandenen Vogel-Skins und Welten, außer den sieben seltenen animierten Skins. Diese gibt es nur für Münzen oder über ihren Erfolg, nicht über Anzeigen oder Echtgeld. Er läuft 60 Minuten nach der verdienten Anzeige ab; gekaufte Inhalte bleiben davon unberührt. Während eines Flugs erscheint keine Anzeige.
- Debug-Builds verwenden Googles Test-Anzeigenblock. Android fordert vor dem Laden den UMP-Datenschutzstatus an, setzt TFUA und Inhaltsbewertung G.
- Der AdMob-App-Status ist noch „Überprüfung nötig“. Live-Anzeigen und echte Werbeerlöse sind erst nach Freigabe, korrekter Store-Verknüpfung und Prüfung der Datenschutzangaben zu erwarten.

## Play Billing

- Die Android-Billing-Library 9.1.0 ist eingebunden. Der Billing-Code in `native/androidApp` (Platform-Implementierung von `platform.Billing`) kann Produktdetails und lokalisierte Preise abfragen, Play-Kaufdialoge öffnen und bestehende Käufe abfragen.
- Einzelne Skins und Welten gehören nach einem Echtgeldkauf dauerhaft dem Spieler. Die App verarbeitet Play-Kaufereignisse und stellt nicht verbrauchte Käufe wieder her. Eine serverseitige Prüfung von Kaufnachweisen und eine dauerhafte serverseitige Gutschriftenliste für Münzpakete fehlen noch. Bis dahin keine Produkte aktivieren oder den Build mit Echtgeldkäufen veröffentlichen.
- Ein „Werbung entfernen“-Kauf entfällt, weil Anzeigen ausschließlich freiwillig sind.

### Freigegebene Preise

| Produkt-ID | Anzeige im Shop | Inhalt | Preis in Deutschland | Kaufart |
| --- | --- | --- | ---: | --- |
| `birdy_coins_500` | 500 Münzen | 500 Münzen für dauerhafte Shop-Freischaltungen | 0,99 € | Einmalkauf, wiederholbar |
| `birdy_coins_1500` | 1.500 Münzen | 1.500 Münzen; rund 16 % günstiger je Münze als das kleine Paket | 2,49 € | Einmalkauf, wiederholbar |
| `birdy_skin_<id>` | Einzelner Vogel-Skin | Dauerhafte Freischaltung des ausgewählten Skins | 0,99–1,99 € | Einmalkauf, dauerhaft |
| `birdy_world_<id>` | Einzelne Welt | Dauerhafte Freischaltung der ausgewählten Welt | 1,99–3,49 € | Einmalkauf, dauerhaft |

**Skin-Preisstufen:** 0,99 € für Himmel, Kardinal, Rotkehlchen, Minze und Koralle; 1,49 € für Flamingo, Papagei, Pinguin, Nachteule und Schneeeule; 1,99 € für Pfau und Goldvogel.

**Welt-Preise:** Winterland 1,99 €, Südsee 2,49 €, Zuckerland 2,99 €, Pilzwald 3,49 €.

Alle diese Inhalte können weiterhin mit erspielten Münzen freigeschaltet werden. Im kostenlosen Spiel gibt es Tagesgeschenke, Missionen und Münzen aus Flügen; eine freiwillige Rewarded Ad gibt 30 Münzen. Echtgeldkäufe sind direkte, dauerhafte Freischaltungen. Der Style-Pass ist ausschließlich über eine freiwillige Anzeige erhältlich und verfällt nach einer Stunde. Preise außerhalb Deutschlands werden von Google Play lokalisiert und im Shop aus Play-Produktdetails angezeigt.

**Status (28. September 2026):** Produktumfang und deutsche Preise wurden vom Herausgeber freigegeben. Das signierte Bundle mit `BILLING`-Berechtigung (versionCode `26092801`) wurde in Play hochgeladen und liegt als nicht ausgelieferter Entwurf im internen Track. Das Dienstkonto hat nun die Birdy-Berechtigung „App-Präsenz im Play Store verwalten“; der zuvor fehlschlagende Preis-API-Aufruf liefert HTTP 200. Der Versuch, `birdy_coins_500` als Entwurf anzulegen, wird weiterhin mit HTTP 400 und „request billing permission“ abgewiesen. Play erkennt die `BILLING`-Berechtigung offenbar erst nach einem ausgelieferten Build; derzeit sind keine Play-Produkte angelegt oder aktiviert.

## Vor einem Test-Release mit Monetarisierung

1. Produktumfang und Preise festlegen, Play-Produkte anlegen und mit lizenzierten Testkonten testen.
2. Kaufnachweise zuverlässig validieren, doppelte Gutschriften und Erstattungen behandeln sowie Käufe nach Neuinstallation wiederherstellen.
3. Datenschutzerklärung veröffentlichen; Play-Datensicherheit und Angaben zu Werbung/Käufen aktualisieren.
4. AdMob-App prüfen und mit dem Play-Store-Eintrag verknüpfen; Test- und Live-Anzeigen auf einem echten Android-Gerät prüfen.
5. Erst nach diesen Schritten die Repository-Variable `BIRDY_MONETIZATION_RELEASE_READY` auf `true` setzen. Bis dahin überspringt der GitHub-Workflow die Play-Veröffentlichung, auch wenn die Änderungen bereits in `main` liegen.
