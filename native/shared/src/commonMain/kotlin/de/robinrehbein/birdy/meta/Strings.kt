package de.robinrehbein.birdy.meta

import de.robinrehbein.birdy.platform.KeyValueStore
import de.robinrehbein.birdy.platform.StorageKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Lang(val code: String) { DE("de"), EN("en") }

/**
 * DE/EN string table (i18n.js). Semantics to preserve (meta.md §3.2): lookup falls back
 * current language -> German -> the raw key; `{name}` placeholders are replaced from [params],
 * missing params become "". Text may contain `[icon]` tags rendered by the UI's rich-text helper.
 */
interface Strings {
    val lang: StateFlow<Lang>
    fun t(key: String, params: Map<String, Any?> = emptyMap()): String
    /** Persists to `birdy-lang`. */
    fun setLang(lang: Lang)
}

/** Scaffold placeholder that echoes keys; kept for tests that don't need the real table. */
class KeyEchoStrings(initial: Lang = Lang.DE) : Strings {
    private val state = MutableStateFlow(initial)
    override val lang: StateFlow<Lang> = state
    override fun t(key: String, params: Map<String, Any?>): String = key
    override fun setLang(lang: Lang) { state.value = lang }
}

/**
 * i18n.js's full DE/EN table (100 keys), `t()`/`L()`/detect()/setLang() semantics (meta.md §3).
 *
 * Lookup order: current language -> German -> the raw key (never falls to English). `{name}`
 * placeholders are substituted with an empty string when missing from [params] (never throws,
 * never leaves the literal `{name}`). Some values embed `[iconName]` tags; splitting those into
 * text/icon runs is the UI's job ([RichText]).
 */
class TableStrings(private val storage: KeyValueStore, deviceLanguage: String) : Strings {
    private val state = MutableStateFlow(detect(storage, deviceLanguage))
    override val lang: StateFlow<Lang> = state

    override fun t(key: String, params: Map<String, Any?>): String {
        val entry = STRINGS[key] ?: NATIVE_STRINGS[key]
        val s = entry?.get(state.value) ?: entry?.de ?: key
        return substitute(s, params)
    }

    override fun setLang(lang: Lang) {
        state.value = lang
        storage.putString(StorageKeys.LANG, lang.code)
    }

    companion object {
        /** `{name}` -> params["name"]?.toString() ?: "" (i18n.js `t()`, meta.md §3.2). */
        fun substitute(s: String, params: Map<String, Any?>): String {
            val out = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '{') {
                    val end = s.indexOf('}', i + 1)
                    if (end > i) {
                        val name = s.substring(i + 1, end)
                        if (name.isNotEmpty() && name.all { it.isLetterOrDigit() || it == '_' }) {
                            out.append(params[name]?.toString() ?: "")
                            i = end + 1
                            continue
                        }
                    }
                }
                out.append(c)
                i++
            }
            return out.toString()
        }

        /** i18n.js `detect()`: `birdy-lang` if valid, else device language startsWith "de". */
        fun detect(storage: KeyValueStore, deviceLanguage: String): Lang {
            val saved = storage.getString(StorageKeys.LANG)
            if (saved == "de") return Lang.DE
            if (saved == "en") return Lang.EN
            return if (deviceLanguage.lowercase().startsWith("de")) Lang.DE else Lang.EN
        }

        /** Keys added by the native app only; kept apart so [STRINGS] still mirrors the legacy table. */
        val NATIVE_STRINGS: Map<String, LocalizedText> = mapOf(
            "remindTitle" to LocalizedText("Birdy", "Birdy"),
            "remindStreak" to LocalizedText("Deine {n}-Tage-Serie läuft heute ab!", "Your {n}-day streak ends today!"),
            "remindGift" to LocalizedText("Dein Tagesgeschenk wartet (+{n} Münzen)", "Your daily gift is waiting (+{n} coins)"),
            "remindOn" to LocalizedText("Erinnerungen an", "Reminders on"),
            "remindOff" to LocalizedText("Erinnerungen aus", "Reminders off"),
            "remindBlocked" to LocalizedText("Mitteilungen in den Systemeinstellungen erlauben", "Allow notifications in system settings"),
        )

        val STRINGS: Map<String, LocalizedText> = mapOf(
            "zoneFlap" to LocalizedText("flattern", "flap"),
            "zoneMove" to LocalizedText("wischen", "swipe"),
            "handFlap" to LocalizedText("Tippen oder hochwischen<br>= flattern", "Tap or swipe up<br>= flap"),
            "handSide" to LocalizedText("Zur Seite wischen<br>= ausweichen", "Swipe sideways<br>= dodge"),
            "best" to LocalizedText("Rekord", "Best"),
            "howto" to LocalizedText(
                "Wische <b>nach oben</b> (oder tippe) zum Flattern,<br /><b>nach links/rechts</b> zum Bahnwechsel.",
                "Swipe <b>up</b> (or tap) to flap,<br />swipe <b>left/right</b> to switch lanes.",
            ),
            "keys" to LocalizedText("Tastatur: Leertaste / ↑ flattern, ← → Spur", "Keyboard: Space / ↑ flap, ← → lane"),
            "legend" to LocalizedText(
                "[rainbow] unverwundbar · [magnet] Münz-Magnet · [mushroom] Mini-Vogel",
                "[rainbow] invincible · [magnet] coin magnet · [mushroom] mini bird",
            ),
            "play" to LocalizedText("Los geht's", "Play"),
            "shop" to LocalizedText("[bird] Shop", "[bird] Shop"),
            "achievements" to LocalizedText("[trophy] Erfolge", "[trophy] Awards"),
            "privacy" to LocalizedText("Datenschutz", "Privacy policy"),
            "adPrivacy" to LocalizedText("Werbe-Datenschutz", "Ad privacy choices"),
            "adAgeSettings" to LocalizedText("Altersgruppe", "Age group"),
            "rewardAd" to LocalizedText("▶ Anzeige ansehen · +30 Münzen ({n}/3 heute)", "▶ Watch an ad · +30 coins ({n}/3 today)"),
            "rewardGranted" to LocalizedText("+30 Münzen für die Anzeige!", "+30 coins for the ad!"),
            "rewardUnavailable" to LocalizedText("Gerade keine Anzeige verfügbar", "No ad available right now"),
            "stylePassAd" to LocalizedText(
                "[play] 1 Stunde alle Vögel & Welten testen · Anzeige ({n}/3 heute)",
                "[play] Try all birds & worlds for 1 hour · ad ({n}/3 today)",
            ),
            "stylePassActive" to LocalizedText("[sparkle] Style-Pass aktiv · noch {n} Min.", "[sparkle] Style Pass active · {n} min left"),
            "stylePassGranted" to LocalizedText(
                "Alle Vögel & Welten für 1 Stunde freigeschaltet (außer seltene)!",
                "All birds & worlds unlocked for 1 hour (except rare ones)!",
            ),
            "realBuy" to LocalizedText("Dauerhaft kaufen · {price} Echtgeld", "Own forever · {price} real money"),
            "removeAdsBuy" to LocalizedText("Automatische Werbung dauerhaft entfernen · {price}", "Remove automatic ads forever · {price}"),
            "removeAdsOwned" to LocalizedText("Automatische Werbung entfernt · freiwillige Anzeigen bleiben", "Automatic ads removed · optional ads remain"),
            "purchaseGranted" to LocalizedText("Dauerhaft freigeschaltet!", "Unlocked permanently!"),
            "purchaseUnavailable" to LocalizedText("Kauf gerade nicht möglich", "Purchase unavailable right now"),
            "achTitle" to LocalizedText("Erfolge", "Awards"),
            "back" to LocalizedText("Zurück", "Back"),
            "tab_skin" to LocalizedText("Farbe", "Colour"),
            "tab_pattern" to LocalizedText("Muster", "Pattern"),
            "tab_hat" to LocalizedText("Kopf", "Hats"),
            "tab_eyes" to LocalizedText("Augen", "Eyes"),
            "tab_beak" to LocalizedText("Schnabel", "Beak"),
            "tab_trail" to LocalizedText("Spuren", "Trails"),
            "tab_world" to LocalizedText("Welten", "Worlds"),
            "tab_pipe" to LocalizedText("Röhren", "Pipes"),
            "tab_upgrade" to LocalizedText("Power", "Power"),
            "tab_dice" to LocalizedText("Zufall", "Random"),
            "kind_skin" to LocalizedText("Farbe", "colour"),
            "kind_pattern" to LocalizedText("Muster", "pattern"),
            "kind_hat" to LocalizedText("Kopfschmuck", "hat"),
            "kind_eyes" to LocalizedText("Augen", "eyes"),
            "kind_beak" to LocalizedText("Schnabel", "beak"),
            "kind_trail" to LocalizedText("Spur", "trail"),
            "kind_world" to LocalizedText("Welt", "world"),
            "kind_pipe" to LocalizedText("Röhren-Design", "pipe design"),
            "level" to LocalizedText("Stufe {n}/{max}", "level {n}/{max}"),
            "upgrade" to LocalizedText("Verbessern · {n}", "Upgrade · {n}"),
            "maxed" to LocalizedText("Maximal", "Maxed out"),
            "needMore" to LocalizedText("Noch {n}", "{n} more"),
            "rare" to LocalizedText("Selten", "Rare"),
            "rareOr" to LocalizedText("[sparkle] Seltener Skin · oder gratis: {text}", "[sparkle] Rare skin · or free: {text}"),
            "rareOwned" to LocalizedText("[sparkle] Seltener animierter Skin", "[sparkle] Rare animated skin"),
            "skinReward" to LocalizedText("+ Skin „{name}“", "+ “{name}” skin"),
            "surprise" to LocalizedText("[gift] Überraschung · {n}", "[gift] Surprise · {n}"),
            "surpriseGot" to LocalizedText("[gift] Neu: {name}!", "[gift] New: {name}!"),
            "swipeHint" to LocalizedText("[hand] Wische nach links/rechts, um die Bahn zu wechseln", "[hand] Swipe left/right to switch lanes"),
            "pause" to LocalizedText("Pause", "Paused"),
            "resume" to LocalizedText("Tippen zum Weiterspielen", "Tap to continue"),
            "continue" to LocalizedText("Weiter", "Continue"),
            "zoneLabel" to LocalizedText("Zone", "Zone"),
            "gameOver" to LocalizedText("Game Over", "Game Over"),
            "score" to LocalizedText("Punkte", "Score"),
            "coins" to LocalizedText("Münzen", "Coins"),
            "newBest" to LocalizedText("Neuer Rekord!", "New best!"),
            "again" to LocalizedText("Nochmal", "Again"),
            "menu" to LocalizedText("Menü", "Menu"),
            "reviveTitle" to LocalizedText("Weiterfliegen?", "Keep flying?"),
            "reviveAd" to LocalizedText("Werbung ansehen", "Watch ad"),
            "reviveCoins" to LocalizedText("Weiter für {n} [coin]", "Continue for {n} [coin]"),
            "reviveNo" to LocalizedText("Nein danke", "No thanks"),
            "doubleCoinsAd" to LocalizedText("+{n} Münzen (Werbung)", "+{n} coins (ad)"),
            "doubleCoinsGranted" to LocalizedText("Münzen verdoppelt!", "Coins doubled!"),
            "missionsSummary" to LocalizedText("Missionen {done}/{total}", "Missions {done}/{total}"),
            "rotate" to LocalizedText("Bitte Handy hochkant halten", "Please hold your phone upright"),
            "missions" to LocalizedText("Tagesmissionen", "Daily missions"),
            "gift" to LocalizedText("[gift] Tagesgeschenk · +{n}", "[gift] Daily gift · +{n}"),
            "streak" to LocalizedText("[fire] Serie: Tag {d} · morgen +{n}", "[fire] Streak: day {d} · tomorrow +{n}"),
            "buy" to LocalizedText("Kaufen · {n}", "Buy · {n}"),
            "selected" to LocalizedText("Ausgewählt", "Selected"),
            "select" to LocalizedText("Auswählen", "Select"),
            "noTrail" to LocalizedText("Keine Spur", "No trail"),
            "tieRecord" to LocalizedText("Rekord eingestellt!", "You tied your best!"),
            "toRecord" to LocalizedText("Nur noch {n} bis zum Rekord!", "Only {n} more to beat your best!"),
            "achUnlocked" to LocalizedText("Erfolg: {name}", "Award: {name}"),
            "zoneReached" to LocalizedText("Zone {n} erreicht: {name}", "Reached zone {n}: {name}"),
            "unlockReady" to LocalizedText("[sparkle] {kind} „{name}“ jetzt freischaltbar! ›", "[sparkle] {kind} “{name}” ready to unlock! ›"),
            "unlockNext" to LocalizedText("Noch {n} [coin] bis {kind} „{name}“", "{n} [coin] more for {kind} “{name}”"),
            "recordToast" to LocalizedText("[trophy] Neuer Rekord!", "[trophy] New best!"),
            "near" to LocalizedText("Knapp!", "Close call!"),
            "zone" to LocalizedText("Zone {n}", "Zone {n}"),
            "tutDone" to LocalizedText("Super! Jetzt allein weiter [star]", "Great! Now on your own [star]"),
            "muteOn" to LocalizedText("Ton an", "Sound on"),
            "muteOff" to LocalizedText("Ton aus", "Sound off"),
            "lang" to LocalizedText("EN", "DE"),
            "langLabel" to LocalizedText("Switch to English", "Auf Deutsch umstellen"),
        )
    }
}

/**
 * Splits a rich-text string on `[name]` tags (icons.js `rich()`) into text/icon runs for the UI
 * to lay out, e.g. in a `Row`/`AnnotatedString`. Unknown tag names are left as literal text.
 */
sealed class RichSegment {
    data class Text(val text: String) : RichSegment()
    data class Icon(val name: String) : RichSegment()
}

object RichText {
    private val TAG = Regex("\\[(\\w+)]")

    fun parse(s: String, knownIcons: Set<String> = Icons.NAMES): List<RichSegment> {
        val segments = mutableListOf<RichSegment>()
        var last = 0
        for (m in TAG.findAll(s)) {
            val name = m.groupValues[1]
            if (name !in knownIcons) continue
            if (m.range.first > last) segments.add(RichSegment.Text(s.substring(last, m.range.first)))
            segments.add(RichSegment.Icon(name))
            last = m.range.last + 1
        }
        if (last < s.length) segments.add(RichSegment.Text(s.substring(last)))
        return segments
    }
}
