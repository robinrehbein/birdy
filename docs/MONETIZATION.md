# Birdy: Monetarisierung

## Anzeigen

- Android: AdMob-App-ID `ca-app-pub-1786159152036324~5284705514`.
- Zwei freiwillige Rewarded-Ad-Blöcke: 30 Münzen (`ca-app-pub-1786159152036324/7854280106`) oder ein einstündiger Style-Pass (`ca-app-pub-1786159152036324/8020434087`).
- Ein Interstitial-Block für Rundenenden (`ca-app-pub-1786159152036324/6477156238`): nur für die lokal gewählte Altersgruppe 16+, frühestens nach 20 Minuten aufsummierter abgeschlossener Flugzeit, nur zwischen Runden, höchstens drei tatsächlich gezeigte Interstitials pro lokalem Tag. Ohne Consent oder geladene Anzeige wird nichts gezeigt. AdMob begrenzt diesen Block zusätzlich auf drei Impressionen pro Nutzer und Tag. Die 13–15-Gruppe erhält keine automatischen Anzeigen.
- Insgesamt höchstens drei belohnte Anzeigen pro lokalem Tag. Der Style-Pass gibt vorübergehend Zugriff auf alle vorhandenen Vogel-Skins und Welten, außer den sieben seltenen animierten Skins. Diese gibt es nur für Münzen oder über ihren Erfolg, nicht über Anzeigen oder Echtgeld. Er läuft 60 Minuten nach der verdienten Anzeige ab; gekaufte Inhalte bleiben davon unberührt. Während eines Flugs erscheint keine Anzeige.
- Debug-Builds verwenden Googles Test-Anzeigenblöcke. Beim ersten Start fragt Birdy lokal nur die Altersgruppe 13–15 oder 16+ ab; ohne Auswahl werden keine Anzeigen geladen. Für 13–15 setzt Android TFUA bei UMP und die entsprechende Altersbehandlung `CHILD` bei AdMob, für 16+ läuft die UMP-Einwilligung. Die Auswahl kann im Startmenü geändert werden. Die maximale Anzeigen-Einstufung bleibt G.
- Die EU-Einwilligungsnachricht „Birdy – EU-Einwilligung“ ist seit 28.09. in AdMob veröffentlicht. Die App ist noch nicht mit dem Play-Eintrag verknüpft; die AdMob-Suche fand sie bisher nicht. AdMob zeigt „Überprüfung nötig“. Live-Anzeigen und echte Werbeerlöse sind erst nach Freigabe und Store-Verknüpfung zu erwarten.

## Play Billing

- Die Android-Billing-Library 9.1.0 ist eingebunden. Die native Brücke kann Produktdetails und lokalisierte Preise abfragen, Play-Kaufdialoge öffnen und bestehende Käufe abfragen.
- Einzelne Skins und Welten sind dauerhaft kaufbar. Die App verarbeitet nur bestätigte `PURCHASED`-Käufe, fragt bestehende Käufe beim Start und nach der Rückkehr in die App erneut ab, bestätigt sie bei Play und entzieht beim erfolgreichen Abgleich nicht mehr vorhandene Kauf-Freischaltungen. Ohne Play-Verbindung bleibt das Spiel spielbar; Kaufangebote werden ausgeblendet. Eine serverseitige Kaufprüfung gibt es nicht. Das begrenzt den Schutz vor manipulierten Clients; Verbrauchsgüter werden deshalb nicht angeboten.
- Der dauerhafte Kauf `birdy_remove_ads` entfernt nur automatische Interstitials. Freiwillige Rewarded Ads für Münzen und Style-Pass bleiben verfügbar. Solange Play dieses Produkt nicht zu 2,99 € in Deutschland aktiv anzeigt, darf das Interstitial-Update nicht auf die Test-Tracks gelangen.

### Freigegebene Preise

| Produkt-ID | Anzeige im Shop | Inhalt | Preis in Deutschland | Kaufart |
| --- | --- | --- | ---: | --- |
| `birdy_skin_<id>` | Einzelner Vogel-Skin | Dauerhafte Freischaltung des ausgewählten Skins | 0,99–1,99 € | Einmalkauf, dauerhaft |
| `birdy_world_<id>` | Einzelne Welt | Dauerhafte Freischaltung der ausgewählten Welt | 1,99–3,49 € | Einmalkauf, dauerhaft |
| `birdy_remove_ads` | Automatische Werbung entfernen | Dauerhaft keine automatischen Anzeigen; freiwillige Belohnungsanzeigen bleiben verfügbar | 2,99 € | Einmalkauf, dauerhaft |

**Skin-Preisstufen:** 0,99 € für Himmel, Kardinal, Rotkehlchen, Minze und Koralle; 1,49 € für Flamingo, Papagei, Pinguin, Nachteule und Schneeeule; 1,99 € für Pfau und Goldvogel.

**Welt-Preise:** Winterland 1,99 €, Südsee 2,49 €, Zuckerland 2,99 €, Pilzwald 3,49 €.

Alle diese Inhalte können weiterhin mit erspielten Münzen freigeschaltet werden. Im kostenlosen Spiel gibt es Tagesgeschenke, Missionen und Münzen aus Flügen; eine freiwillige Rewarded Ad gibt 30 Münzen. Echtgeldkäufe sind direkte, dauerhafte Freischaltungen. Der Style-Pass ist ausschließlich über eine freiwillige Anzeige erhältlich und verfällt nach einer Stunde. Preise außerhalb Deutschlands werden von Google Play lokalisiert und im Shop aus Play-Produktdetails angezeigt.

**Status (29. September 2026):** Ausschließlich die native Kotlin-App (`native/androidApp`) wird ausgeliefert. Münzpakete wurden entfernt. Version 2.0.0 (`versionCode 207250181`) wurde laut Play-API als abgeschlossener Release auf `internal` und `alpha` eingetragen; der zugehörige GitHub-Workflow war grün. Weitere Merges lösen automatisch neuere Test-Releases aus; die aktuellen Versionscodes stehen in den Play-Tracks. Alle zwölf normalen Skin- und vier Weltprodukte wurden mit den freigegebenen deutschen Preisen und automatisch umgerechneten regionalen Preisen angelegt und als `ACTIVE` zurückgelesen. Es gibt keine Produkte für seltene Skins oder Münzpakete. Ein älterer, nicht ausgelieferter Capacitor-Entwurf wurde beim Test-Release ersetzt.

**Interstitial-Update:** Kotlin-Code und Tests für die 20-Minuten-Regel, Altersgrenze 16+ und den dauerhaften Werbefrei-Kauf sind im Branch `codex/interstitial-ads` umgesetzt, aber noch nicht veröffentlicht. Der neue AdMob-Block ist angelegt. Das Play-Produkt ist noch nicht aktiv: Die Konsole rechnete einen Referenzpreis von 2,99 € für Deutschland auf 3,59 € hoch. Nur 2,99 € in Deutschland sind freigegeben; daher das Update bis zur korrekten Produktaktivierung nicht mergen oder hochladen.

## Vor einem Test-Release mit Monetarisierung

1. ✅ Test-Build mit `BILLING`-Berechtigung auf `internal` und `alpha` ausliefern; ✅ ausschließlich die freigegebenen dauerhaften Play-Produkte anlegen. Test mit lizenzierten Testkonten offen.
2. Wiederherstellung, Kaufbestätigung, ausstehende Käufe und Erstattungen auf einem Play-Testgerät prüfen.
3. Gespeicherte Play-Datensicherheitsangaben zur Prüfung einreichen und auf einem Testgerät verifizieren; die Datenschutzerklärung und Werbe-Angabe sind bereits aktualisiert.
4. AdMob-App prüfen und mit dem Play-Store-Eintrag verknüpfen; Test- und Live-Anzeigen auf einem echten Android-Gerät prüfen.
5. ✅ `BIRDY_MONETIZATION_RELEASE_READY=true` und `PLAY_PUBLISH_ENABLED=true`. Jeder Push auf `main` startet damit den signierten Kotlin-Upload auf `internal` und `alpha`; Produktion ist ausgeschlossen. Der erste freigeschaltete Lauf war grün. Den Test auf einem Play-Gerät abschließen.
