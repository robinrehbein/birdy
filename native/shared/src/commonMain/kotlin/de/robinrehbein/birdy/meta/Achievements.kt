package de.robinrehbein.birdy.meta

/**
 * `ACHIEVEMENTS` (progress.js lines 12-35, meta.md §1.5): 21 lifetime milestones, in declaration
 * order (load-bearing: `unlockAchievements()` iterates in this order). 7 also grant a free rare
 * skin the moment they fire.
 */
object Achievements {
    val ALL: List<Achievement> = listOf(
        Achievement("score10", "bird", LocalizedText("Abgehoben", "Lift-off"), LocalizedText("10 Punkte in einem Flug", "Score 10 in one flight"), "bestScore", 10, 30),
        Achievement("score25", "bird", LocalizedText("Flugschüler", "Student Pilot"), LocalizedText("25 Punkte in einem Flug", "Score 25 in one flight"), "bestScore", 25, 60),
        Achievement("score50", "star", LocalizedText("Himmelsstürmer", "Sky Racer"), LocalizedText("50 Punkte in einem Flug", "Score 50 in one flight"), "bestScore", 50, 120),
        Achievement("score100", "crown", LocalizedText("Legende", "Legend"), LocalizedText("100 Punkte in einem Flug", "Score 100 in one flight"), "bestScore", 100, 300),
        Achievement("zone4", "flower", LocalizedText("Weltenbummler", "Globetrotter"), LocalizedText("Erreiche den Blütenhain (Zone 4)", "Reach the Blossom Grove (zone 4)"), "bestZone", 3, 150),
        Achievement("near10", "bolt", LocalizedText("Haarscharf", "Hair's Breadth"), LocalizedText("10× „Knapp!“ insgesamt", "10 close calls in total"), "nearTotal", 10, 40),
        Achievement("chain5", "fire", LocalizedText("Nervenkitzel", "Thrill Seeker"), LocalizedText("5× „Knapp!“ in Folge", "5 close calls in a row"), "bestChain", 5, 150),
        Achievement("powers3", "rainbow", LocalizedText("Power-Sammler", "Power Collector"), LocalizedText("3 Power-ups in einem Flug", "3 power-ups in one flight"), "bestPowerups", 3, 60),
        Achievement("plants25", "cactus", LocalizedText("Gärtner", "Gardener"), LocalizedText("An 25 Stachelkakteen vorbei", "Pass 25 spiky cacti"), "plantsTotal", 25, 80),
        Achievement("coins500", "coin", LocalizedText("Sparschwein", "Piggy Bank"), LocalizedText("500 Münzen eingesammelt", "Collect 500 coins"), "coinsTotal", 500, 80),
        Achievement("coins2000", "coin", LocalizedText("Schatzmeister", "Treasurer"), LocalizedText("2000 Münzen eingesammelt", "Collect 2000 coins"), "coinsTotal", 2000, 200),
        Achievement("runs50", "play", LocalizedText("Dauerflieger", "Frequent Flyer"), LocalizedText("50 Runden gespielt", "Play 50 rounds"), "runs", 50, 100),
        Achievement("streak7", "calendar", LocalizedText("Stammgast", "Regular"), LocalizedText("7 Tage Geschenk-Serie", "7-day gift streak"), "bestStreak", 7, 200),
        Achievement("unlock5", "palette", LocalizedText("Sammler", "Collector"), LocalizedText("5 Shop-Artikel freigeschaltet", "Unlock 5 shop items"), "unlocks", 5, 100),
        Achievement("rareToadstool", "cactus", LocalizedText("Kakteenflüsterer", "Cactus Whisperer"), LocalizedText("An 150 Stachelkakteen vorbei", "Pass 150 spiky cacti"), "plantsTotal", 150, 100, skin = "toadstool"),
        Achievement("rareBasketball", "play", LocalizedText("Dauerbrenner", "Marathon Flyer"), LocalizedText("200 Runden gespielt", "Play 200 rounds"), "runs", 200, 100, skin = "basketball"),
        Achievement("rareFootball", "bolt", LocalizedText("Nerven aus Stahl", "Nerves of Steel"), LocalizedText("100× „Knapp!“ insgesamt", "100 close calls in total"), "nearTotal", 100, 100, skin = "football"),
        Achievement("rareWater", "rainbow", LocalizedText("Power-Profi", "Power Pro"), LocalizedText("5 Power-ups in einem Flug", "5 power-ups in one flight"), "bestPowerups", 5, 100, skin = "water"),
        Achievement("rareLava", "coin", LocalizedText("Goldgräber", "Gold Digger"), LocalizedText("10.000 Münzen eingesammelt", "Collect 10,000 coins"), "coinsTotal", 10000, 100, skin = "lava"),
        Achievement("rareDiamond", "fire", LocalizedText("Eiskalt", "Ice Cold"), LocalizedText("10× „Knapp!“ in Folge", "10 close calls in a row"), "bestChain", 10, 100, skin = "diamond"),
        Achievement("rareGalaxy", "star", LocalizedText("Sternenflieger", "Star Flyer"), LocalizedText("200 Punkte in einem Flug", "Score 200 in one flight"), "bestScore", 200, 100, skin = "galaxy"),
    )

    /** `RUN_MAX_STATS`: lifetime stat <- run field, aggregated with max() in `finishRun`. */
    val RUN_MAX_STATS: Map<String, String> = mapOf(
        "bestScore" to "score", "bestZone" to "zone", "bestChain" to "bestChain", "bestPowerups" to "powerups",
    )

    /** `RUN_SUM_STATS`: lifetime stat <- run field, aggregated by addition in `finishRun`. */
    val RUN_SUM_STATS: Map<String, String> = mapOf(
        "nearTotal" to "near", "plantsTotal" to "plants", "coinsTotal" to "coins",
    )
}

/** `KINDS = Object.keys(CATALOG)` declaration order (catalog.js), load-bearing for §1.3 step 6. */
val KINDS_ORDER: List<Kind> = listOf(Kind.Skin, Kind.Pattern, Kind.Hat, Kind.Eyes, Kind.Beak, Kind.Trail, Kind.World, Kind.Pipe)
