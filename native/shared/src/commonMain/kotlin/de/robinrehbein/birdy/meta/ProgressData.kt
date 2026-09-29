package de.robinrehbein.birdy.meta

import kotlinx.serialization.Serializable

/**
 * Persisted player progress: the post-migration JSON shape of `birdy-progress` (meta.md §1.2),
 * serialized with kotlinx.serialization under the same key. Immutable; the repository replaces
 * the whole value on every mutation so the UI can observe it through a StateFlow.
 */
@Serializable
data class ProgressData(
    val coins: Int = 0,
    val best: Int = 0,
    val runs: Int = 0,
    val owned: List<String> = listOf("sunny"),
    val skin: String = "sunny",
    val missions: MissionDay? = null,
    val gift: Gift = Gift(),
    val rewardedAds: RewardedAds = RewardedAds(),
    /** Epoch millis; style pass active while now < this. */
    val stylePassUntil: Long = 0,
    val paidProducts: List<String> = emptyList(),
    val tutorialDone: Boolean = false,
    val trails: List<String> = listOf("none"),
    val trail: String = "none",
    val stats: Stats = Stats(),
    val achieved: List<String> = emptyList(),
    /** Owned ids per kind id. */
    val items: Map<String, List<String>> = emptyMap(),
    /** Equipped id per kind id. */
    val equip: Map<String, String> = emptyMap(),
    val upgrades: Map<String, Int> = emptyMap(),
)

@Serializable
data class Gift(val last: String = "", val streak: Int = 0)

@Serializable
data class RewardedAds(val day: String = "", val count: Int = 0)

@Serializable
data class Stats(
    val bestScore: Int = 0,
    val bestZone: Int = 0,
    val nearTotal: Int = 0,
    val bestChain: Int = 0,
    val bestPowerups: Int = 0,
    val plantsTotal: Int = 0,
    val coinsTotal: Int = 0,
    val bestStreak: Int = 0,
)

@Serializable
data class MissionDay(val date: String, val list: List<Mission>)

@Serializable
data class Mission(val id: String, val goal: Int, val progress: Int = 0, val reward: Int, val done: Boolean = false)

data class Achievement(
    val id: String,
    val icon: String,
    val name: LocalizedText,
    val text: LocalizedText,
    val stat: String,
    val goal: Int,
    val reward: Int,
    val skin: String? = null,
)

/**
 * Per-run counters handed to `finishRun` (main-a.md §6 step 12). Field names equal the JS `run`
 * keys because mission templates look them up by name ([valueOf]).
 */
data class RunStats(
    val score: Int = 0,
    val coins: Int = 0,
    val powerups: Int = 0,
    val plants: Int = 0,
    val moving: Int = 0,
    val starRows: Int = 0,
    val near: Int = 0,
    val bestChain: Int = 0,
    val zone: Int = 0,
) {
    fun valueOf(stat: String): Int = when (stat) {
        "score" -> score
        "coins" -> coins
        "powerups" -> powerups
        "plants" -> plants
        "moving" -> moving
        "starRows" -> starRows
        "near" -> near
        "bestChain" -> bestChain
        "zone" -> zone
        else -> 0
    }
}

/** `finishRun` result (meta.md §1.10 step 9); missions carry their rendered text. */
data class RunResult(
    val isBest: Boolean,
    val completed: List<Mission>,
    val achievements: List<Achievement>,
)

/** `claimGift()` result. */
data class GiftClaim(val amount: Int, val streak: Int)
