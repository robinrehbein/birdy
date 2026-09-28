package de.robinrehbein.birdy.meta

/** Shop item kinds in `KINDS` declaration order (load-bearing: tab order, normalization loop). */
enum class Kind(val id: String) {
    Skin("skin"), Pattern("pattern"), Hat("hat"), Eyes("eyes"), Beak("beak"),
    Trail("trail"), World("world"), Pipe("pipe");

    companion object {
        fun of(id: String): Kind? = entries.firstOrNull { it.id == id }
    }
}

/** `{ de, en }` text pair (i18n.js `L()` picks one). */
data class LocalizedText(val de: String, val en: String) {
    fun get(lang: Lang): String = if (lang == Lang.EN) en else de
}

/** One shop entry; colours are exact JS `0xRRGGBB` ints. See docs/native/golden/meta-catalog.json. */
sealed class CatalogItem {
    abstract val id: String
    abstract val name: LocalizedText
    abstract val price: Int
    abstract val kind: Kind
}

data class SkinItem(
    override val id: String,
    override val name: LocalizedText,
    override val price: Int,
    val body: Int, val belly: Int, val wing: Int, val cover: Int, val tail: Int, val beak: Int, val beakLow: Int,
    val metal: Boolean = false,
    /** Animated premium effect id (skinfx.js), null for plain skins. */
    val fx: String? = null,
    val rare: Boolean = false,
    /** CSS gradient of the shop swatch for rare skins (render natively in the UI). */
    val swatch: String? = null,
) : CatalogItem() { override val kind get() = Kind.Skin }

/** Pattern / hat / eyes / beak items: shown with a raw emoji [icon] in the shop. */
data class AccessoryItem(
    override val kind: Kind,
    override val id: String,
    override val name: LocalizedText,
    override val price: Int,
    val icon: String,
) : CatalogItem()

data class TrailItem(
    override val id: String,
    override val name: LocalizedText,
    override val price: Int,
    /** Empty for the "none" trail. */
    val colors: List<Int>,
    val size: Float = 0f,
    val life: Float = 0f,
    val gravity: Float = 0f,
    val speed: Float = 0f,
) : CatalogItem() { override val kind get() = Kind.Trail }

/** Pipe colour set (also the shape of a world's `pipes` override). */
data class PipeColors(val pipe: Int, val light: Int, val dark: Int, val metal: Boolean = false)

data class PipeItem(
    override val id: String,
    override val name: LocalizedText,
    override val price: Int,
    val colors: PipeColors,
) : CatalogItem() { override val kind get() = Kind.Pipe }

/** Sky/light palette shared by shop worlds and the fixed zone biomes (biomes.js). */
data class Palette(
    val top: Int, val horizon: Int,
    val hemiSky: Int, val hemiGround: Int, val hemiI: Float,
    val sun: Int, val sunI: Float,
    val tint: Int, val clouds: Int, val grass: Int, val track: Int,
)

data class WorldItem(
    override val id: String,
    override val name: LocalizedText,
    override val price: Int,
    /** icons.js icon name. */
    val icon: String,
    /** Scenery theme key (world.md §5.3). */
    val scenery: String,
    val palette: Palette,
    /** Optional [track, stripes, border] road colours. */
    val road: List<Int>? = null,
    val pipes: PipeColors? = null,
) : CatalogItem() { override val kind get() = Kind.World }

data class Upgrade(val id: String, val icon: String, val name: LocalizedText, val text: LocalizedText, val prices: List<Int>)

/** One fixed zone/biome (biomes.js `BIOMES`); procedural per-run scenery rotation, distinct from
 * the purchasable [WorldItem] "world" reskins (meta.md §2.3). */
data class ZoneBiome(
    val name: LocalizedText,
    val scenery: String,
    val palette: Palette,
)

/**
 * Static shop data (catalog.js). Every kind's first entry is free and always owned.
 */
object Catalog {
    const val UPGRADE_MAX = 3

    val skins: List<SkinItem> = listOf(
        SkinItem("sunny", LocalizedText("Sunny", "Sunny"), 0, 0xf7d23e, 0xfff3c4, 0xfff6d5, 0xf6e3a1, 0xf2c230, 0xf57c21, 0xe0521b),
        SkinItem("sky", LocalizedText("Himmel", "Sky"), 100, 0x4aa8f0, 0xe8f6ff, 0xdff1ff, 0x9fd2fa, 0x2f86d0, 0xf5a623, 0xe07b1b),
        SkinItem("cardinal", LocalizedText("Kardinal", "Cardinal"), 250, 0xe8453c, 0xffd7c9, 0xffe3dc, 0xf28b82, 0xc4302b, 0xffc93c, 0xf0a020),
        SkinItem("robin", LocalizedText("Rotkehlchen", "Robin"), 300, 0x9a6b4a, 0xff8a3d, 0xe9d6c4, 0xb88a66, 0x7d5238, 0x4a3b33, 0x33271f),
        SkinItem("mint", LocalizedText("Minze", "Mint"), 400, 0x5fd39a, 0xeafff3, 0xe3fff0, 0xa6ecc8, 0x3bb37b, 0xff8a5c, 0xe8643a),
        SkinItem("coral", LocalizedText("Koralle", "Coral"), 500, 0xff7f6b, 0xfff0e6, 0xffe5dc, 0xffb3a3, 0xe8604e, 0x5ad1ff, 0x2fa8d9),
        SkinItem("flamingo", LocalizedText("Flamingo", "Flamingo"), 600, 0xff8fb8, 0xffe6f0, 0xfff0f6, 0xffc2d8, 0xf2649a, 0x4a3b47, 0x2f2530),
        SkinItem("parrot", LocalizedText("Papagei", "Parrot"), 750, 0x3cc45a, 0xffe14a, 0xff5a4a, 0x4ab8ff, 0x2f86d0, 0xf2eee0, 0x3a3a3a),
        SkinItem("penguin", LocalizedText("Pinguin", "Penguin"), 900, 0x3a4250, 0xffffff, 0x505a6c, 0x2e3440, 0x2e3440, 0xffa31a, 0xf07b12),
        SkinItem("night", LocalizedText("Nachteule", "Night Owl"), 1000, 0x5b4b8a, 0xd9d0f5, 0xc7bdf0, 0x8f80c9, 0x44376e, 0xffc93c, 0xe8a820),
        SkinItem("snowy", LocalizedText("Schneeeule", "Snowy Owl"), 1200, 0xf4f7fb, 0xffffff, 0xdde6f0, 0xc9d6e3, 0xb8c6d6, 0x4a4a4a, 0x2e2e2e),
        SkinItem("peacock", LocalizedText("Pfau", "Peacock"), 1400, 0x1f8fb0, 0x7be0c8, 0x3ccfa0, 0x2a6fd0, 0x1ea06a, 0xffd24a, 0xe8a820),
        SkinItem("gold", LocalizedText("Goldvogel", "Golden Bird"), 2000, 0xffc629, 0xfff1b0, 0xffe57a, 0xffd23d, 0xe0a100, 0xff7a1a, 0xd9530f, metal = true),
        SkinItem(
            "toadstool", LocalizedText("Fliegenpilz", "Toadstool"), 3000, 0xe0302a, 0xfff3e0, 0xe0302a, 0xc4241f, 0xc4241f, 0xfff3e0, 0xe8d8c0,
            fx = "toadstool", rare = true,
            swatch = "radial-gradient(circle at 30% 35%, #fff 0 9%, transparent 10%), radial-gradient(circle at 68% 62%, #fff 0 12%, transparent 13%), radial-gradient(circle at 70% 25%, #fff 0 6%, transparent 7%), #e0302a",
        ),
        SkinItem(
            "basketball", LocalizedText("Basketball", "Basketball"), 3500, 0xf26b1d, 0xffa860, 0xf26b1d, 0xd9530f, 0xd9530f, 0x3a2418, 0x2a1a10,
            fx = "basketball", rare = true,
            swatch = "linear-gradient(90deg, transparent 47%, #3a2418 47% 53%, transparent 53%), linear-gradient(transparent 47%, #3a2418 47% 53%, transparent 53%), radial-gradient(circle at 35% 30%, #ffa860, #f26b1d 60%)",
        ),
        SkinItem(
            "football", LocalizedText("Fußball", "Football"), 4000, 0xf4f4f4, 0xffffff, 0xe6e6e6, 0x2a2a2a, 0x2a2a2a, 0xf57c21, 0xe0521b,
            fx = "football", rare = true,
            swatch = "radial-gradient(circle at 50% 50%, #222 0 16%, transparent 17%), radial-gradient(circle at 12% 15%, #222 0 13%, transparent 14%), radial-gradient(circle at 88% 20%, #222 0 13%, transparent 14%), radial-gradient(circle at 20% 90%, #222 0 13%, transparent 14%), radial-gradient(circle at 85% 88%, #222 0 13%, transparent 14%), #f4f4f4",
        ),
        SkinItem(
            "water", LocalizedText("Wasser", "Water"), 4500, 0x1e7fd6, 0xbfe9ff, 0x1e7fd6, 0x3aa0f0, 0x1466b0, 0xffc93c, 0xe8a820,
            fx = "water", rare = true,
            swatch = "radial-gradient(circle at 30% 70%, transparent 0 7%, #e8f7ff 8% 11%, transparent 12%), radial-gradient(circle at 65% 40%, transparent 0 5%, #e8f7ff 6% 9%, transparent 10%), repeating-linear-gradient(160deg, #1e7fd6 0 10px, #4ab8ff 10px 16px)",
        ),
        SkinItem(
            "lava", LocalizedText("Lava", "Lava"), 5000, 0x3a2420, 0x5a3028, 0x3a2420, 0x2a1a18, 0x2a1a18, 0x2a2a2a, 0x1a1a1a,
            fx = "lava", rare = true,
            swatch = "linear-gradient(35deg, transparent 40%, #ff8a1a 40% 46%, transparent 46%), linear-gradient(-50deg, transparent 55%, #ffb03a 55% 60%, transparent 60%), linear-gradient(80deg, transparent 20%, #ff6a10 20% 25%, transparent 25%), #3a2420",
        ),
        SkinItem(
            "diamond", LocalizedText("Diamant", "Diamond"), 5500, 0x9fe0ff, 0xe6fbff, 0x9fe0ff, 0x7fd0f5, 0x7fd0f5, 0xbfefff, 0x8fd3f0,
            fx = "diamond", rare = true,
            swatch = "conic-gradient(from 20deg, #e6fbff, #8fd3ff, #ffffff, #b8a8ff, #e6fbff, #7fe0f0, #ffffff, #e6fbff)",
        ),
        SkinItem(
            "galaxy", LocalizedText("Galaxie", "Galaxy"), 6000, 0x2a1a60, 0x6a3a9a, 0x2a1a60, 0x4a2a8a, 0x4a2a8a, 0xffe14a, 0xf0b820,
            fx = "galaxy", rare = true,
            swatch = "radial-gradient(circle at 25% 30%, #fff 0 3%, transparent 4%), radial-gradient(circle at 70% 60%, #fff 0 4%, transparent 5%), radial-gradient(circle at 60% 20%, #fff 0 2%, transparent 3%), radial-gradient(circle at 30% 75%, #8f63d6, transparent 45%), radial-gradient(circle at 75% 35%, #4ab8ff, transparent 40%), #1a1040",
        ),
    )

    val patterns: List<AccessoryItem> = listOf(
        AccessoryItem(Kind.Pattern, "plain", LocalizedText("Schlicht", "Plain"), 0, "○"),
        AccessoryItem(Kind.Pattern, "cheeks", LocalizedText("Bäckchen", "Rosy Cheeks"), 150, "😊"),
        AccessoryItem(Kind.Pattern, "spots", LocalizedText("Tupfen", "Spots"), 300, "⚬"),
        AccessoryItem(Kind.Pattern, "stripes", LocalizedText("Streifen", "Stripes"), 450, "≡"),
        AccessoryItem(Kind.Pattern, "mask", LocalizedText("Räubermaske", "Bandit Mask"), 600, "🦝"),
        AccessoryItem(Kind.Pattern, "heart", LocalizedText("Herz am Rücken", "Back Heart"), 800, "💗"),
    )

    val hats: List<AccessoryItem> = listOf(
        AccessoryItem(Kind.Hat, "none", LocalizedText("Ohne", "None"), 0, "✕"),
        AccessoryItem(Kind.Hat, "crest", LocalizedText("Federschopf", "Crest"), 200, "🪶"),
        AccessoryItem(Kind.Hat, "flower", LocalizedText("Blume", "Flower"), 300, "🌼"),
        AccessoryItem(Kind.Hat, "party", LocalizedText("Partyhut", "Party Hat"), 450, "🎉"),
        AccessoryItem(Kind.Hat, "cap", LocalizedText("Propellermütze", "Propeller Cap"), 650, "🧢"),
        AccessoryItem(Kind.Hat, "tophat", LocalizedText("Zylinder", "Top Hat"), 850, "🎩"),
        AccessoryItem(Kind.Hat, "viking", LocalizedText("Wikingerhelm", "Viking Helmet"), 1100, "⚔️"),
        AccessoryItem(Kind.Hat, "crown", LocalizedText("Krone", "Crown"), 1500, "👑"),
        AccessoryItem(Kind.Hat, "halo", LocalizedText("Heiligenschein", "Halo"), 1800, "😇"),
    )

    val eyes: List<AccessoryItem> = listOf(
        AccessoryItem(Kind.Eyes, "normal", LocalizedText("Kulleraugen", "Big Eyes"), 0, "👀"),
        AccessoryItem(Kind.Eyes, "lashes", LocalizedText("Wimpern", "Lashes"), 150, "✨"),
        AccessoryItem(Kind.Eyes, "brows", LocalizedText("Entschlossen", "Determined"), 250, "😠"),
        AccessoryItem(Kind.Eyes, "shades", LocalizedText("Sonnenbrille", "Shades"), 500, "🕶️"),
        AccessoryItem(Kind.Eyes, "hearts", LocalizedText("Herzbrille", "Heart Glasses"), 700, "😍"),
        AccessoryItem(Kind.Eyes, "goggles", LocalizedText("Fliegerbrille", "Flight Goggles"), 900, "🥽"),
    )

    val beaks: List<AccessoryItem> = listOf(
        AccessoryItem(Kind.Beak, "round", LocalizedText("Rundschnabel", "Round Beak"), 0, "🐤"),
        AccessoryItem(Kind.Beak, "duck", LocalizedText("Entenschnabel", "Duck Bill"), 300, "🦆"),
        AccessoryItem(Kind.Beak, "hook", LocalizedText("Adlerschnabel", "Eagle Beak"), 600, "🦅"),
        AccessoryItem(Kind.Beak, "toucan", LocalizedText("Tukan", "Toucan"), 1000, "🌴"),
    )

    val trails: List<TrailItem> = listOf(
        TrailItem("none", LocalizedText("Keine Spur", "No trail"), 0, emptyList()),
        TrailItem("sparkle", LocalizedText("Funkeln", "Sparkle"), 150, listOf(0xfff176, 0xffffff, 0xffd400), 0.08f, 0.45f, 0f, 0.8f),
        TrailItem("bubbles", LocalizedText("Blasen", "Bubbles"), 300, listOf(0xbfe9ff, 0xe8f7ff, 0x8fd3ff), 0.14f, 0.8f, 2.5f, 0.5f),
        TrailItem("hearts", LocalizedText("Herzchen", "Hearts"), 400, listOf(0xff5a8a, 0xff9ab8, 0xffffff), 0.12f, 0.7f, 1.5f, 0.6f),
        TrailItem("confetti", LocalizedText("Konfetti", "Confetti"), 500, listOf(0xff5a5a, 0x5ad1ff, 0xffd84a, 0x7be07b, 0xc58bff), 0.09f, 0.7f, -4f, 2f),
        TrailItem("snow", LocalizedText("Schneeflocken", "Snowflakes"), 600, listOf(0xffffff, 0xe3f4ff, 0xbfe3ff), 0.1f, 1.1f, -1.2f, 0.9f),
        TrailItem("leaves", LocalizedText("Herbstlaub", "Autumn Leaves"), 700, listOf(0xe8772e, 0xf2a93b, 0xd9492f), 0.12f, 0.9f, -2f, 1.2f),
        TrailItem("neon", LocalizedText("Neon", "Neon"), 850, listOf(0x39ff14, 0xff2fd6, 0x00e5ff), 0.08f, 0.5f, 0f, 1.4f),
        TrailItem("stardust", LocalizedText("Sternenstaub", "Stardust"), 1000, listOf(0xc58bff, 0xffffff, 0x8f7bff), 0.07f, 0.9f, 0.5f, 0.6f),
        TrailItem("rainbow", LocalizedText("Regenbogen", "Rainbow"), 1200, listOf(0xff5a5a, 0xffa43a, 0xffe14a, 0x6fd86a, 0x4ab8ff, 0x9a7bff), 0.11f, 0.6f, 0f, 0.3f),
        TrailItem("fire", LocalizedText("Feuerschweif", "Fire Tail"), 1400, listOf(0xff7a1a, 0xffc93c, 0xff3d2e), 0.13f, 0.4f, 3f, 0.9f),
        TrailItem("goldrain", LocalizedText("Goldregen", "Gold Rain"), 1800, listOf(0xffd400, 0xffe57a, 0xfff6c4), 0.1f, 0.8f, -5f, 1f),
    )

    val worlds: List<WorldItem> = listOf(
        WorldItem(
            "park", LocalizedText("Stadtpark", "City Park"), 0, "tree", "park",
            Palette(0x2a9bd0, 0xa6e4ea, 0xdff6ff, 0x6a8f3a, 1.4f, 0xfff4d6, 2.2f, 0xffffff, 0xffffff, 0x73bf2e, 0xffffff),
        ),
        WorldItem(
            "winter", LocalizedText("Winterland", "Winterland"), 1200, "snowflake", "winter",
            Palette(0x5aa9e6, 0xe3f2ff, 0xffffff, 0x9fb4c8, 1.45f, 0xfff6e8, 2.0f, 0xffffff, 0xffffff, 0xe6f0fb, 0xe4ecf8),
            road = listOf(0xeef4fb, 0xd6e4f2, 0xbfe3ff),
            pipes = PipeColors(0x5fc6e6, 0xe6fbff, 0x3a93b8),
        ),
        WorldItem(
            "beach", LocalizedText("Südsee", "South Seas"), 1800, "palm", "beach",
            Palette(0x1fa5e0, 0xbff3f0, 0xe8fbff, 0xc9b27a, 1.45f, 0xfff4d6, 2.3f, 0xffffff, 0xffffff, 0xf2dc9b, 0xffffff),
            road = listOf(0xf7e6b0, 0xefd48a, 0x5fd3d0),
            pipes = PipeColors(0x2fbfb3, 0x9ff0e6, 0x1f8a80),
        ),
        WorldItem(
            "candy", LocalizedText("Zuckerland", "Candyland"), 2400, "lollipop", "candy",
            Palette(0xf07ab8, 0xffe3f1, 0xfff0f8, 0xc98fb8, 1.45f, 0xfff0f6, 2.0f, 0xffffff, 0xfff0fa, 0xffb3d9, 0xfff0f6),
            road = listOf(0xffd1e6, 0xffb3d4, 0xffffff),
            pipes = PipeColors(0xff6fa8, 0xffffff, 0xd9407c),
        ),
        WorldItem(
            "mushroom", LocalizedText("Pilzwald", "Mushroom Woods"), 3000, "mushroom", "mushroom",
            Palette(0x4a6fd0, 0xc8f0d8, 0xeafff2, 0x5a8a5a, 1.4f, 0xfff0c8, 2.1f, 0xffffff, 0xf0e8ff, 0x6fc45a, 0xfffaf0),
            road = listOf(0xd6e6a6, 0xbfd188, 0x8fe070),
            pipes = PipeColors(0xe0453a, 0xff9a8a, 0xa82a22),
        ),
    )

    val pipes: List<PipeItem> = listOf(
        PipeItem("green", LocalizedText("Klassisch", "Classic"), 0, PipeColors(0x73bf2e, 0xb2ea6c, 0x4f8a1f)),
        PipeItem("blue", LocalizedText("Himmelblau", "Sky Blue"), 250, PipeColors(0x3f9be0, 0x9fd6ff, 0x2a6fb0)),
        PipeItem("orange", LocalizedText("Orange", "Orange"), 400, PipeColors(0xf28a2e, 0xffc27a, 0xc4611a)),
        PipeItem("purple", LocalizedText("Lila", "Purple"), 550, PipeColors(0x8f63d6, 0xc6a8ff, 0x6440a8)),
        PipeItem("wood", LocalizedText("Holz", "Wood"), 700, PipeColors(0xa8733f, 0xd9a86a, 0x7a4f2a)),
        PipeItem("stone", LocalizedText("Stein", "Stone"), 900, PipeColors(0x9aa3ad, 0xd0d7de, 0x6a737d)),
        PipeItem("candy", LocalizedText("Bonbon", "Candy"), 1100, PipeColors(0xff6fa8, 0xffffff, 0xd9407c)),
        PipeItem("ice", LocalizedText("Eis", "Ice"), 1300, PipeColors(0x5fc6e6, 0xe6fbff, 0x3a93b8)),
        PipeItem("lava", LocalizedText("Lava", "Lava"), 1600, PipeColors(0xe0452e, 0xffc93c, 0x8f2418)),
        PipeItem("gold", LocalizedText("Gold", "Gold"), 2200, PipeColors(0xffc629, 0xfff1b0, 0xd99a00)),
    )

    val upgrades: List<Upgrade> = listOf(
        Upgrade("star", "rainbow", LocalizedText("Regenbogen", "Rainbow"), LocalizedText("+1,5 s unverwundbar pro Stufe", "+1.5 s invincible per level"), listOf(300, 800, 1800)),
        Upgrade("magnet", "magnet", LocalizedText("Magnet", "Magnet"), LocalizedText("+3 s und größere Reichweite pro Stufe", "+3 s and wider reach per level"), listOf(300, 800, 1800)),
        Upgrade("mini", "mushroom", LocalizedText("Mini-Vogel", "Mini Bird"), LocalizedText("+3 s klein pro Stufe", "+3 s tiny per level"), listOf(300, 800, 1800)),
        Upgrade("luck", "clover", LocalizedText("Glückspilz", "Lucky"), LocalizedText("Power-ups tauchen öfter auf", "Power-ups show up more often"), listOf(500, 1200, 2500)),
    )

    /** biomes.js `BIOMES`: 4 fixed zones the run rotates through every ~10-15 rows. */
    val zoneBiomes: List<ZoneBiome> = listOf(
        ZoneBiome(
            LocalizedText("Stadtpark", "City Park"), "park",
            Palette(0x2a9bd0, 0xa6e4ea, 0xdff6ff, 0x6a8f3a, 1.4f, 0xfff4d6, 2.2f, 0xffffff, 0xffffff, 0x73bf2e, 0xffffff),
        ),
        ZoneBiome(
            LocalizedText("Herbstwald", "Autumn Forest"), "autumn",
            Palette(0x4d6fc4, 0xffc08a, 0xffe2c8, 0x7a6a3a, 1.35f, 0xffb877, 2.4f, 0xfff0e2, 0xffd2dc, 0x9aae36, 0xfff0dc),
        ),
        ZoneBiome(
            LocalizedText("Canyon", "Canyon"), "canyon",
            Palette(0x5b53b8, 0xf2a6cc, 0xebd8ff, 0x5d6a80, 1.35f, 0xffd6f2, 1.9f, 0xf6ecff, 0xf6dcff, 0xd9a86a, 0xfff0e0),
        ),
        ZoneBiome(
            LocalizedText("Blütenhain", "Blossom Grove"), "blossom",
            Palette(0x36b3c9, 0xd6f6d2, 0xf0fff2, 0x6a9a4a, 1.45f, 0xfff9e3, 2.1f, 0xffffff, 0xffffff, 0x7fd35a, 0xffffff),
        ),
    )

    fun items(kind: Kind): List<CatalogItem> = when (kind) {
        Kind.Skin -> skins
        Kind.Pattern -> patterns
        Kind.Hat -> hats
        Kind.Eyes -> eyes
        Kind.Beak -> beaks
        Kind.Trail -> trails
        Kind.World -> worlds
        Kind.Pipe -> pipes
    }

    fun find(kind: Kind, id: String): CatalogItem? = items(kind).firstOrNull { it.id == id }
    fun free(kind: Kind): CatalogItem = items(kind).first()
}
