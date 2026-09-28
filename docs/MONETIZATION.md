# Birdy: Monetarisierung

## Anzeigen

- Android: AdMob-App-ID `ca-app-pub-1786159152036324~5284705514`.
- Zwei freiwillige Rewarded-Ad-Blöcke: 30 Münzen (`ca-app-pub-1786159152036324/7854280106`) oder ein einstündiger Style-Pass (`ca-app-pub-1786159152036324/8020434087`).
- Insgesamt höchstens drei belohnte Anzeigen pro lokalem Tag. Der Style-Pass gibt vorübergehend Zugriff auf alle vorhandenen Vogel-Skins und Welten, außer den sieben seltenen animierten Skins. Diese gibt es nur für Münzen oder über ihren Erfolg, nicht über Anzeigen oder Echtgeld. Er läuft 60 Minuten nach der verdienten Anzeige ab; gekaufte Inhalte bleiben davon unberührt. Während eines Flugs erscheint keine Anzeige.
- Debug-Builds verwenden Googles Test-Anzeigenblock. Android fordert vor dem Laden den UMP-Datenschutzstatus an, setzt TFUA und Inhaltsbewertung G.
- Der AdMob-App-Status ist noch „Überprüfung nötig“. Live-Anzeigen und echte Werbeerlöse sind erst nach Freigabe, korrekter Store-Verknüpfung und Prüfung der Datenschutzangaben zu erwarten.

## Play Billing

- Die Android-Billing-Library 9.1.0 ist eingebunden. Die native Brücke kann Produktdetails und lokalisierte Preise abfragen, Play-Kaufdialoge öffnen und bestehende Käufe abfragen.
- Einzelne Skins und Welten sind dauerhaft kaufbar. Die App verarbeitet nur bestätigte `PURCHASED`-Käufe, fragt bestehende Käufe beim Start und nach der Rückkehr in die App erneut ab, bestätigt sie bei Play und entzieht beim erfolgreichen Abgleich nicht mehr vorhandene Kauf-Freischaltungen. Ohne Play-Verbindung bleibt das Spiel spielbar; Kaufangebote werden ausgeblendet. Eine serverseitige Kaufprüfung gibt es nicht. Das begrenzt den Schutz vor manipulierten Clients; Verbrauchsgüter werden deshalb nicht angeboten.
- Ein „Werbung entfernen“-Kauf entfällt, weil Anzeigen ausschließlich freiwillig sind.

### Freigegebene Preise

| Produkt-ID | Anzeige im Shop | Inhalt | Preis in Deutschland | Kaufart |
| --- | --- | --- | ---: | --- |
| `birdy_skin_<id>` | Einzelner Vogel-Skin | Dauerhafte Freischaltung des ausgewählten Skins | 0,99–1,99 € | Einmalkauf, dauerhaft |
| `birdy_world_<id>` | Einzelne Welt | Dauerhafte Freischaltung der ausgewählten Welt | 1,99–3,49 € | Einmalkauf, dauerhaft |

**Skin-Preisstufen:** 0,99 € für Himmel, Kardinal, Rotkehlchen, Minze und Koralle; 1,49 € für Flamingo, Papagei, Pinguin, Nachteule und Schneeeule; 1,99 € für Pfau und Goldvogel.

**Welt-Preise:** Winterland 1,99 €, Südsee 2,49 €, Zuckerland 2,99 €, Pilzwald 3,49 €.

Alle diese Inhalte können weiterhin mit erspielten Münzen freigeschaltet werden. Im kostenlosen Spiel gibt es Tagesgeschenke, Missionen und Münzen aus Flügen; eine freiwillige Rewarded Ad gibt 30 Münzen. Echtgeldkäufe sind direkte, dauerhafte Freischaltungen. Der Style-Pass ist ausschließlich über eine freiwillige Anzeige erhältlich und verfällt nach einer Stunde. Preise außerhalb Deutschlands werden von Google Play lokalisiert und im Shop aus Play-Produktdetails angezeigt.

**Status (28. September 2026):** Der freizugebende Build ist ausschließlich die native Kotlin-App (`native/androidApp`). Die zuvor geplanten Münzpakete wurden aus ihr entfernt. Ihr Release-Bundle baut lokal; es wurde noch nicht auf einen Track hochgeladen. Ein älterer Capacitor-Entwurf mit `BILLING`-Berechtigung (versionCode `26092801`) liegt nicht ausgeliefert im internen Track und wird nicht veröffentlicht. Das Dienstkonto hat die Birdy-Berechtigung „App-Präsenz im Play Store verwalten“; der Preis-API-Aufruf liefert HTTP 200. Ein früherer Versuch, ein Münzpaket anzulegen, wurde mit HTTP 400 und „request billing permission“ abgewiesen; dabei entstand kein Produkt. Play muss zunächst den Kotlin-Build mit `BILLING`-Berechtigung auf einem Test-Track ausliefern, bevor die dauerhaften Produkte angelegt werden können.

## Vor einem Test-Release mit Monetarisierung

1. Test-Build mit `BILLING`-Berechtigung ausliefern, danach ausschließlich die freigegebenen dauerhaften Play-Produkte anlegen und mit lizenzierten Testkonten testen.
2. Wiederherstellung, Kaufbestätigung, ausstehende Käufe und Erstattungen auf einem Play-Testgerät prüfen.
3. Datenschutzerklärung veröffentlichen; Play-Datensicherheit und Angaben zu Werbung/Käufen aktualisieren.
4. AdMob-App prüfen und mit dem Play-Store-Eintrag verknüpfen; Test- und Live-Anzeigen auf einem echten Android-Gerät prüfen.
5. Für den ersten Kotlin-Testupload nach Abschluss der Play-Pflichtangaben `BIRDY_MONETIZATION_RELEASE_READY` auf `true` setzen. Danach Produkte anlegen und den Test auf einem Play-Gerät abschließen. Bis zur Freigabe überspringt der Workflow die Veröffentlichung.
