package de.robinrehbein.birdy.meta

import de.robinrehbein.birdy.platform.Clock
import de.robinrehbein.birdy.platform.KeyValueStore
import de.robinrehbein.birdy.platform.StorageKeys
import de.robinrehbein.birdy.platform.purchase.ProductIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

// --- lenient JSON field readers (progress.js's forgiving `load()`, meta.md §1.3) ---
// Each accessor coerces a wrong-typed/missing field to `null` rather than throwing, so one bad
// field never discards the rest of a save (matching e.g. `Array.isArray(x) ? x : []`).

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
private fun JsonObject?.child(key: String): JsonObject? = this?.get(key)?.obj()
private fun JsonObject?.str(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject?.intOrNull(key: String): Int? = (this?.get(key) as? JsonPrimitive)?.doubleOrNull?.toInt()
private fun JsonObject?.longOrNull(key: String): Long? = (this?.get(key) as? JsonPrimitive)?.doubleOrNull?.toLong()
private fun JsonObject?.boolOrNull(key: String): Boolean? = (this?.get(key) as? JsonPrimitive)?.booleanOrNull
private fun JsonObject?.strList(key: String): List<String>? =
    (this?.get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
private fun JsonObject?.strMap(key: String): Map<String, String>? =
    (this?.get(key) as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap()
private fun JsonObject?.strListMap(key: String): Map<String, List<String>>? =
    (this?.get(key) as? JsonObject)?.mapValues { (_, v) ->
        (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    }
private fun JsonObject?.intMap(key: String): Map<String, Int>? =
    (this?.get(key) as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.doubleOrNull?.toInt()?.let { k to it } }?.toMap()

private fun parseMissionDay(o: JsonObject?): MissionDay? {
    if (o == null) return null
    val date = o.str("date") ?: return null
    val list = (o["list"] as? JsonArray)?.mapNotNull { el ->
        val m = el.obj() ?: return@mapNotNull null
        val id = m.str("id") ?: return@mapNotNull null
        val goal = m.intOrNull("goal") ?: return@mapNotNull null
        val reward = m.intOrNull("reward") ?: return@mapNotNull null
        Mission(id, goal, m.intOrNull("progress") ?: 0, reward, m.boolOrNull("done") ?: false)
    } ?: return null
    return MissionDay(date, list)
}

/**
 * [ProgressRepository] backed by [KeyValueStore] key `birdy-progress` (progress.js, meta.md §1).
 * Loads once at construction, keeps the model in memory ([data]) and persists after every
 * mutating call — never re-reads storage on a getter.
 */
class LocalProgressRepository(
    private val storage: KeyValueStore,
    private val clock: Clock,
) : ProgressRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(load())
    override val data: StateFlow<ProgressData> = state

    // --- 1.3 load()/defaulting ---

    private fun load(): ProgressData {
        val raw = storage.getString(StorageKeys.PROGRESS)
        val o = raw?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }?.obj()

        val coins = o.intOrNull("coins") ?: 0
        val savedBest = o.intOrNull("best") ?: 0
        val runs = o.intOrNull("runs") ?: 0
        val owned = o.strList("owned") ?: listOf("sunny")
        val skinLegacy = o.str("skin") ?: "sunny"
        val missions = parseMissionDay(o.child("missions"))
        val giftObj = o.child("gift")
        val gift = Gift(giftObj.str("last") ?: "", giftObj.intOrNull("streak") ?: 0)
        val rewardedAdsObj = o.child("rewardedAds")
        val rewardedAds = RewardedAds(rewardedAdsObj.str("day") ?: "", rewardedAdsObj.intOrNull("count") ?: 0)
        val doubleAdsObj = o.child("doubleCoinsAds")
        val doubleCoinsAds = RewardedAds(doubleAdsObj.str("day") ?: "", doubleAdsObj.intOrNull("count") ?: 0)
        val stylePassUntil = o.longOrNull("stylePassUntil") ?: 0L
        val paidProducts = o.strList("paidProducts") ?: emptyList()
        val tutorialDone = o.boolOrNull("tutorialDone") ?: false
        val trailsLegacy = o.strList("trails") ?: listOf("none")
        val trailLegacy = o.str("trail") ?: "none"
        val achieved = o.strList("achieved") ?: emptyList()

        val items = (o.strListMap("items") ?: emptyMap()).toMutableMap()
        val equip = (o.strMap("equip") ?: emptyMap()).toMutableMap()

        // Legacy (pre-shop) -> current shop-model migration (meta.md §1.3 step 5), idempotent.
        items["skin"] = ((items["skin"] ?: emptyList()) + owned).distinct()
        items["trail"] = ((items["trail"] ?: emptyList()) + trailsLegacy).distinct()
        if (equip["skin"] == null) equip["skin"] = skinLegacy
        if (equip["trail"] == null) equip["trail"] = trailLegacy

        // Per-kind normalization (step 6): free item always owned/first; equip falls back to free
        // unless it's a real, currently-accessible catalog item.
        for (kind in KINDS_ORDER) {
            val k = kind.id
            val free = Catalog.free(kind).id
            val ownedForKind = items[k] ?: emptyList()
            if (free !in ownedForKind) items[k] = listOf(free) + ownedForKind
            val trialAccess = (kind == Kind.Skin || kind == Kind.World) && stylePassUntil > clock.nowMillis()
            val equippedId = equip[k]
            val paidAccess = paidProducts.contains("birdy_${k}_$equippedId")
            val existsInCatalog = equippedId != null && Catalog.find(kind, equippedId) != null
            val ownsIt = equippedId != null && items[k]!!.contains(equippedId)
            if (!existsInCatalog || (!ownsIt && !paidAccess && !trialAccess)) equip[k] = free
        }

        val upgrades = o.intMap("upgrades") ?: emptyMap()

        // Stats default per-field (step 8): bestScore defaults to the *pre-legacy-merge* best.
        val statsObj = o.child("stats")
        val stats = Stats(
            bestScore = statsObj.intOrNull("bestScore") ?: savedBest,
            bestZone = statsObj.intOrNull("bestZone") ?: 0,
            nearTotal = statsObj.intOrNull("nearTotal") ?: 0,
            bestChain = statsObj.intOrNull("bestChain") ?: 0,
            bestPowerups = statsObj.intOrNull("bestPowerups") ?: 0,
            plantsTotal = statsObj.intOrNull("plantsTotal") ?: 0,
            coinsTotal = statsObj.intOrNull("coinsTotal") ?: 0,
            bestStreak = statsObj.intOrNull("bestStreak") ?: 0,
        )

        // Step 9: one-way `birdy-best` legacy-key merge, applied after stats default.
        val legacyBest = storage.getString(StorageKeys.LEGACY_BEST)?.trim()?.toDoubleOrNull()?.toInt() ?: 0
        val best = maxOf(savedBest, legacyBest)

        return ProgressData(
            coins = coins, best = best, runs = runs, owned = owned, skin = skinLegacy,
            missions = missions, gift = gift, rewardedAds = rewardedAds, doubleCoinsAds = doubleCoinsAds, stylePassUntil = stylePassUntil,
            paidProducts = paidProducts, tutorialDone = tutorialDone,
            trails = trailsLegacy, trail = trailLegacy, stats = stats, achieved = achieved,
            items = items, equip = equip, upgrades = upgrades,
        )
    }

    /** Sets [state] and persists; on a storage failure, [state] still keeps the new value (as in
     * progress.js — `save()`'s boolean is ignored by nearly every caller). Returns whether the
     * write succeeded, for the handful of callers that must rollback themselves. */
    private fun commit(next: ProgressData): Boolean {
        state.value = next
        return runCatching { storage.putString(StorageKeys.PROGRESS, json.encodeToString(next)) }.getOrDefault(false)
    }

    private fun today(): String = clock.today().key()
    private fun yesterday(): String = clock.today().keyOffset(-1)

    private fun statValue(stat: String): Int {
        val d = state.value
        return when (stat) {
            "runs" -> d.runs
            "unlocks" -> KINDS_ORDER.sumOf { (d.items[it.id]?.size ?: 1) - 1 } + d.upgrades.values.sum()
            else -> statValueOf(d.stats, stat)
        }
    }

    private fun statValueOf(stats: Stats, stat: String): Int = when (stat) {
        "bestScore" -> stats.bestScore
        "bestZone" -> stats.bestZone
        "nearTotal" -> stats.nearTotal
        "bestChain" -> stats.bestChain
        "bestPowerups" -> stats.bestPowerups
        "plantsTotal" -> stats.plantsTotal
        "coinsTotal" -> stats.coinsTotal
        "bestStreak" -> stats.bestStreak
        else -> 0
    }

    /** §1.5.2 `unlockAchievements()`: mutates [state] in place (caller commits). Returns unlocked. */
    private fun unlockInto(d0: ProgressData): Pair<ProgressData, List<Achievement>> {
        var d = d0
        val unlocked = mutableListOf<Achievement>()
        for (a in Achievements.ALL) {
            if (a.id in d.achieved) continue
            if (statValue(a.stat) < a.goal) continue
            var items = d.items
            if (a.skin != null && a.skin !in (items["skin"] ?: emptyList())) {
                items = items + ("skin" to (items["skin"] ?: emptyList()) + a.skin)
            }
            d = d.copy(achieved = d.achieved + a.id, coins = d.coins + a.reward, items = items)
            state.value = d // statValue() reads state.value, so the next iteration sees this update
            unlocked += a
        }
        return d to unlocked
    }

    // --- shop (meta.md §1.4) ---

    override fun equipped(kind: Kind): CatalogItem {
        val id = state.value.equip[kind.id]
        val item = id?.let { Catalog.find(kind, it) }
        return if (item != null && owns(kind, id)) item else Catalog.free(kind)
    }

    override fun permanentlyOwns(kind: Kind, id: String): Boolean {
        val d = state.value
        return (d.items[kind.id]?.contains(id) == true) || d.paidProducts.contains("birdy_${kind.id}_$id")
    }

    /** Style pass grants access to every id of `skin`/`world` while active — except rare animated
     * skins, which stay earned (coins or their achievement), matching progress.js's current
     * `owns()` (`!CATALOG[kind].find(x => x.id === id)?.rare`). */
    override fun owns(kind: Kind, id: String): Boolean {
        if (permanentlyOwns(kind, id)) return true
        if (stylePassMinutesLeft <= 0 || (kind != Kind.Skin && kind != Kind.World)) return false
        val item = Catalog.find(kind, id)
        val isRare = (item as? SkinItem)?.rare == true
        return !isRare
    }

    override fun buy(kind: Kind, id: String): Boolean {
        val item = Catalog.find(kind, id) ?: return false
        val d = state.value
        if (permanentlyOwns(kind, id) || d.coins < item.price) return false
        commit(d.copy(coins = d.coins - item.price, items = d.items + (kind.id to (d.items[kind.id] ?: emptyList()) + id), equip = d.equip + (kind.id to id)))
        return true
    }

    override fun select(kind: Kind, id: String): Boolean {
        if (Catalog.find(kind, id) == null || !owns(kind, id)) return false
        val d = state.value
        commit(d.copy(equip = d.equip + (kind.id to id)))
        return true
    }

    override fun buySurprise(price: Int): Boolean {
        val d = state.value
        if (d.coins < price) return false
        commit(d.copy(coins = d.coins - price))
        return true
    }

    override fun spendCoins(amount: Int): Boolean {
        val d = state.value
        if (amount < 0 || d.coins < amount) return false
        commit(d.copy(coins = d.coins - amount))
        return true
    }

    override fun grant(kind: Kind, id: String) {
        val d = state.value
        val owned = d.items[kind.id] ?: emptyList()
        val items = if (id in owned) d.items else d.items + (kind.id to owned + id)
        commit(d.copy(items = items, equip = d.equip + (kind.id to id)))
    }

    override fun level(upgradeId: String): Int = state.value.upgrades[upgradeId] ?: 0

    override fun upgradePrice(upgradeId: String): Int? {
        val u = Catalog.upgrades.firstOrNull { it.id == upgradeId } ?: return null
        val lvl = level(upgradeId)
        return if (lvl < Catalog.UPGRADE_MAX) u.prices[lvl] else null
    }

    override fun buyUpgrade(upgradeId: String): Boolean {
        val price = upgradePrice(upgradeId) ?: return false
        val d = state.value
        if (d.coins < price) return false
        commit(d.copy(coins = d.coins - price, upgrades = d.upgrades + (upgradeId to (level(upgradeId) + 1))))
        return true
    }

    // --- missions (meta.md §1.7) ---

    /** Regenerates today's missions if stale, persisting only when it actually regenerated. */
    private fun currentMissions(): MissionDay {
        val d = state.value
        val existing = d.missions
        if (existing != null && existing.date == today()) return existing
        val fresh = Missions.dailyMissions(today(), d.best)
        commit(d.copy(missions = fresh))
        return fresh
    }

    override fun missions(): List<Mission> = currentMissions().list

    /** No [Lang] is available at this layer (frozen interface); returns the German text, matching
     * i18n.js's ultimate fallback language. UI code wanting the active language should call
     * `Missions.byId(m.id).text(m.goal).get(lang)` directly. */
    override fun missionText(m: Mission): String = Missions.byId(m.id).text(m.goal).de

    override fun wouldComplete(run: RunStats): List<String> {
        val hits = mutableListOf<String>()
        for (m in currentMissions().list) {
            if (m.done) continue
            val tpl = Missions.byId(m.id)
            val value = if (tpl.stat == "runs") 0 else run.valueOf(tpl.stat)
            val p = if (tpl.per == MissionPer.RUN) maxOf(m.progress, value) else m.progress + value
            if (p >= m.goal) hits += m.id
        }
        return hits
    }

    override fun wouldUnlock(run: RunStats): List<Achievement> {
        val d = state.value
        val hits = mutableListOf<Achievement>()
        for (a in Achievements.ALL) {
            if (a.id in d.achieved) continue
            var v = statValue(a.stat)
            Achievements.RUN_MAX_STATS[a.stat]?.let { key -> v = maxOf(v, run.valueOf(key)) }
            Achievements.RUN_SUM_STATS[a.stat]?.let { key -> v += run.valueOf(key) }
            if (v >= a.goal) hits += a
        }
        return hits
    }

    override fun checkAchievements(): List<Achievement> {
        val (next, unlocked) = unlockInto(state.value)
        if (unlocked.isNotEmpty()) commit(next) else state.value = next
        return unlocked
    }

    override fun achievements(): List<Pair<Achievement, Int>> =
        Achievements.ALL.map { it to minOf(it.goal, statValue(it.stat)) }

    override fun setTutorialDone() {
        commit(state.value.copy(tutorialDone = true))
    }

    // --- daily gift & streak (meta.md §1.8) ---

    override fun giftAvailable(): Boolean = state.value.gift.last != today()

    override val streak: Int
        get() {
            val g = state.value.gift
            return if (g.last == today() || g.last == yesterday()) g.streak else 0
        }

    override fun giftAmount(streak: Int): Int = GIFT_BASE + GIFT_STEP * (minOf(streak, GIFT_MAX_STREAK) - 1)

    override fun claimGift(): GiftClaim? {
        if (!giftAvailable()) return null
        val d = state.value
        val newStreak = if (d.gift.last == yesterday()) d.gift.streak + 1 else 1
        val amount = giftAmount(newStreak)
        commit(
            d.copy(
                gift = Gift(today(), newStreak),
                coins = d.coins + amount,
                stats = d.stats.copy(bestStreak = maxOf(d.stats.bestStreak, newStreak)),
            ),
        )
        return GiftClaim(amount, newStreak)
    }

    // --- rewarded ads / style pass (meta.md §1.9) ---

    override val rewardedAdsLeft: Int
        get() {
            val d = state.value
            val count = if (d.rewardedAds.day == today()) d.rewardedAds.count else 0
            return maxOf(0, REWARDED_ADS_PER_DAY - count)
        }

    private fun useRewardedSlot(): Boolean {
        if (rewardedAdsLeft == 0) return false
        val d = state.value
        commit(d.copy(rewardedAds = RewardedAds(today(), REWARDED_ADS_PER_DAY - rewardedAdsLeft + 1)))
        return true
    }

    override fun grantRewardedCoins(): Int {
        if (!useRewardedSlot()) return 0
        val d = state.value
        commit(d.copy(coins = d.coins + REWARDED_COINS))
        return REWARDED_COINS
    }

    override val doubleCoinsAdsLeft: Int
        get() {
            val a = state.value.doubleCoinsAds
            return maxOf(0, DOUBLE_COINS_ADS_PER_DAY - if (a.day == today()) a.count else 0)
        }

    override fun grantDoubleCoins(runCoins: Int): Int {
        if (runCoins <= 0 || doubleCoinsAdsLeft == 0) return 0
        val d = state.value
        commit(d.copy(
            coins = d.coins + runCoins,
            doubleCoinsAds = RewardedAds(today(), DOUBLE_COINS_ADS_PER_DAY - doubleCoinsAdsLeft + 1),
        ))
        return runCoins
    }

    override val stylePassMinutesLeft: Int
        get() {
            val leftMs = state.value.stylePassUntil - clock.nowMillis()
            return maxOf(0L, ceilDiv(leftMs, 60_000L)).toInt()
        }

    override fun grantStylePass(): Boolean {
        if (stylePassMinutesLeft > 0 || !useRewardedSlot()) return false
        val d = state.value
        commit(d.copy(stylePassUntil = clock.nowMillis() + STYLE_PASS_MS))
        return true
    }

    // --- billing entitlements (platform.md §2.6) ---

    private val paidProductRegex = Regex("^birdy_(skin|world)_([a-z]+)$")

    override fun grantPaidProduct(productId: String): Boolean? {
        if (productId == ProductIds.REMOVE_ADS) {
            val previous = state.value
            val wasOwned = productId in previous.paidProducts
            val paid = if (wasOwned) previous.paidProducts else previous.paidProducts + productId
            if (commit(previous.copy(paidProducts = paid))) return !wasOwned
            state.value = previous
            return null
        }
        val match = paidProductRegex.matchEntire(productId) ?: return false
        val kind = Kind.of(match.groupValues[1]) ?: return false
        val id = match.groupValues[2]
        val item = Catalog.find(kind, id) ?: return false
        if (item.price <= 0 || (item as? SkinItem)?.rare == true) return false
        val wasOwned = permanentlyOwns(kind, id)
        val d = state.value
        val paidProducts = if (productId in d.paidProducts) d.paidProducts else d.paidProducts + productId
        val ok = commit(d.copy(paidProducts = paidProducts, equip = d.equip + (kind.id to id)))
        if (ok) return !wasOwned
        state.value = d
        return null
    }

    override fun syncPaidProducts(productIds: List<String>): Boolean {
        val filtered = productIds.distinct().filter { id ->
            if (id == ProductIds.REMOVE_ADS) return@filter true
            val m = paidProductRegex.matchEntire(id) ?: return@filter false
            val kind = Kind.of(m.groupValues[1]) ?: return@filter false
            val item = Catalog.find(kind, m.groupValues[2]) ?: return@filter false
            item.price > 0 && (item as? SkinItem)?.rare != true
        }
        val previous = state.value
        if (commit(previous.copy(paidProducts = filtered))) return true
        state.value = previous
        return false
    }

    companion object {
        private const val GIFT_BASE = 20
        private const val GIFT_STEP = 10
        private const val GIFT_MAX_STREAK = 7
        private const val REWARDED_ADS_PER_DAY = 3
        const val DOUBLE_COINS_ADS_PER_DAY = 5
        private const val REWARDED_COINS = 30
        private const val STYLE_PASS_MS = 60L * 60L * 1000L

        private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b
    }

    // --- run-end aggregation (meta.md §1.10) ---

    override fun finishRun(run: RunStats): RunResult {
        var d = state.value
        val isBest = run.score > d.best
        var stats = d.stats
        for ((stat, key) in Achievements.RUN_MAX_STATS) {
            stats = setStat(stats, stat, maxOf(statValueOf(stats, stat), run.valueOf(key)))
        }
        for ((stat, key) in Achievements.RUN_SUM_STATS) {
            stats = setStat(stats, stat, statValueOf(stats, stat) + run.valueOf(key))
        }
        d = d.copy(runs = d.runs + 1, coins = d.coins + run.coins, best = if (isBest) run.score else d.best, stats = stats)
        state.value = d // so statValue()/currentMissions() below see the aggregated stats

        val missionDay = currentMissions()
        val completed = mutableListOf<Mission>()
        val newList = missionDay.list.map { m ->
            if (m.done) return@map m
            val tpl = Missions.byId(m.id)
            val value = if (tpl.stat == "runs") 1 else run.valueOf(tpl.stat)
            var progress = if (tpl.per == MissionPer.RUN) maxOf(m.progress, value) else m.progress + value
            if (progress >= m.goal) {
                progress = m.goal
                val done = m.copy(progress = progress, done = true)
                d = d.copy(coins = d.coins + m.reward)
                completed += done
                done
            } else {
                m.copy(progress = progress)
            }
        }
        d = d.copy(missions = MissionDay(missionDay.date, newList))
        state.value = d

        val (afterAch, unlocked) = unlockInto(d)
        commit(afterAch)
        return RunResult(isBest, completed, unlocked)
    }

    private fun setStat(s: Stats, stat: String, value: Int): Stats = when (stat) {
        "bestScore" -> s.copy(bestScore = value)
        "bestZone" -> s.copy(bestZone = value)
        "nearTotal" -> s.copy(nearTotal = value)
        "bestChain" -> s.copy(bestChain = value)
        "bestPowerups" -> s.copy(bestPowerups = value)
        "plantsTotal" -> s.copy(plantsTotal = value)
        "coinsTotal" -> s.copy(coinsTotal = value)
        "bestStreak" -> s.copy(bestStreak = value)
        else -> s
    }
}
