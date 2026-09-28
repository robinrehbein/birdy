package de.robinrehbein.birdy.view.bird

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import de.robinrehbein.birdy.meta.Catalog
import de.robinrehbein.birdy.meta.SkinItem
import kotlin.math.sin

/**
 * The low-poly bird built from primitives, facing -Z (bird.js `createBird`). Used by [BirdView]
 * for the game bird and by the thumbnail renderer for shop tiles.
 *
 * [root] is the group the caller positions/rotates/scales. Game thread only.
 */
class BirdRig {
    val root = Node("bird")

    private val yellow = flatMat(0xf7d23e)
    private val cream = flatMat(0xfff3c4)
    private val white = flatMat(0xffffff)
    private val black = flatMat(0x222222)
    private val beakMat = flatMat(0xf57c21)
    private val beakLowMat = flatMat(0xe0521b)
    private val wingMat = flatMat(0xfff6d5)
    private val coverMat = flatMat(0xf6e3a1)
    private val tailMat = smoothMat(0xf2c230)
    private val patDark = flatMat(0xf2c230)
    private val patLight = flatMat(0xfff3c4)

    private val wings = ArrayList<Node>()
    private val parts: BirdParts
    private val glowMats = LinkedHashSet<StandardMaterial>()

    /** Skin shader effects for body/belly/wing/cover/tail. */
    val skinFx = SkinFx()

    /** Recoloured per skin in place (bird.js `skinMats`), in JS key order. */
    private val skinMats = linkedMapOf(
        "body" to yellow, "belly" to cream, "wing" to wingMat, "cover" to coverMat,
        "tail" to tailMat, "beak" to beakMat, "beakLow" to beakLowMat,
    )

    var look = BirdLook()
        private set
    var skin: SkinItem = Catalog.skins[0]
        private set

    init {
        root.add(Mesh(Primitives.sphere(0.6, 14, 10), yellow, "body").scaled(1.0, 0.9, 1.15))
        root.add(Mesh(Primitives.sphere(0.42, 12, 8), cream, "belly").at(0.0, -0.2, -0.3))
        for (side in listOf(-1.0, 1.0)) {
            root.add(Mesh(Primitives.sphere(0.22, 12, 8), white, "eye").at(side * 0.38, 0.25, -0.38))
            root.add(Mesh(Primitives.sphere(0.09, 8, 6), black, "pupil").at(side * 0.5, 0.27, -0.5))
        }
        // Beak: two rounded mandibles, faceted like the body.
        val beakTop = Mesh(Primitives.sphere(0.26, 10, 7), beakMat).scaled(1.15, 0.5, 1.25).at(0.0, 0.0, -0.66)
        val beakBottom = Mesh(Primitives.sphere(0.24, 10, 7), beakLowMat).scaled(1.0, 0.42, 1.05).at(0.0, -0.15, -0.6)
        val roundBeak = group(beakTop, beakBottom)
        root.add(roundBeak)

        // Wings: one shared feather geometry; the covert layer is a smaller copy on top.
        val wingGeo = BirdShapes.wingGeometry()
        for (side in listOf(-1, 1)) {
            val pivot = Node("wingPivot").at(side * 0.48, 0.08, 0.08)
            val wing = Mesh(wingGeo, wingMat, "wing")
            wing.scale.x = side.toFloat()
            wing.rotation.z = side * -0.15f
            val cover = Mesh(wingGeo, coverMat, "cover").scaled(0.62, 1.0, 0.7).at(0.0, 0.045, -0.04)
            wing.add(cover)
            pivot.add(wing)
            pivot.userData[SIDE] = side
            root.add(pivot)
            wings += pivot
        }

        // Tail: three rounded feathers fanned out.
        val tailGeo = Primitives.sphere(0.2, 14, 10)
        for ((x, rotY) in listOf(-0.12 to 0.35, 0.0 to 0.0, 0.12 to -0.35)) {
            root.add(Mesh(tailGeo, tailMat, "tail").scaled(0.7, 0.3, 1.5).at(x * 1.2, 0.2, 0.8, 0.45, rotY))
        }

        parts = BirdParts.build(beakMat, beakLowMat, patDark, patLight, roundBeak)
        for (kind in BirdLook.WORKSHOP_KINDS) {
            for (obj in parts.of(kind).values) {
                if (obj === roundBeak) continue
                obj.visible = false
                root.add(obj)
            }
        }

        root.traverse { n ->
            val m = (n as? Mesh)?.material as? StandardMaterial ?: return@traverse
            if (m.userData[BirdParts.OWN_GLOW] != true) glowMats += m
        }
        listOf("body", "belly", "wing", "cover", "tail").forEachIndexed { i, key -> skinFx.add(skinMats.getValue(key), i) }
        setLook(BirdLook())
    }

    /** Shows exactly one part per workshop category. Unknown ids hide the whole category. */
    fun setLook(next: BirdLook) {
        look = next
        for (kind in BirdLook.WORKSHOP_KINDS) {
            val id = next.idOf(kind)
            for ((key, obj) in parts.of(kind)) obj.visible = key == id
        }
    }

    /** Wing beat for [phase] (radians, unwrapped), plus the cap propeller and the halo bob. */
    fun animateWings(phase: Double) {
        val a = (sin(phase) * 0.8).toFloat()
        for (w in wings) w.rotation.z = (w.userData[SIDE] as Int) * a
        parts.propeller.rotation.y = (phase * 2.2).toFloat()
        parts.haloRing.position.y = (0.34 + sin(phase * 0.35) * 0.04).toFloat()
    }

    /**
     * Emissive glow on every bird material except the halo (`ownGlow`): `emissive = color * intensity`
     * in linear space; null switches it off.
     */
    fun setGlow(color: Color?, intensity: Float = 0.6f) {
        for (m in glowMats) {
            if (color == null) m.emissive.setRGB(0f, 0f, 0f)
            else m.emissive.set(color).multiplyScalar(intensity)
        }
    }

    /** Recolours the shared materials for [skin] (colours, metal/roughness, wing rule, fx). */
    fun setSkin(skin: SkinItem) {
        this.skin = skin
        patDark.color.setHex(skin.tail)
        patLight.color.setHex(if (skin.belly == skin.body) 0xffffff else skin.belly)
        skinFx.set(skin.fx)
        val wc = wingColor(skin)
        for ((key, m) in skinMats) {
            m.color.setHex(
                when (key) {
                    "body" -> skin.body
                    "belly" -> skin.belly
                    "wing" -> wc.wing
                    "cover" -> wc.cover
                    "tail" -> skin.tail
                    "beak" -> skin.beak
                    else -> skin.beakLow
                },
            )
            m.metalness = if (skin.metal && key != "beak" && key != "beakLow") 0.55f else 0f
            m.roughness = if (skin.metal) 0.3f else if (key == "tail") 0.45f else 0.55f
        }
    }

    // Test hooks (read-only views of the rig state).
    internal fun wingRotations(): List<Float> = wings.map { it.rotation.z }
    internal val propellerRotY: Float get() = parts.propeller.rotation.y
    internal val haloRingY: Float get() = parts.haloRing.position.y
    internal fun part(kindParts: (BirdParts) -> Map<String, Node>): Map<String, Node> = kindParts(parts)
    internal fun material(key: String): StandardMaterial = skinMats.getValue(key)
    internal val glowMaterials: Set<StandardMaterial> get() = glowMats
    internal val haloMaterial: StandardMaterial get() = parts.haloRing.material as StandardMaterial

    private companion object {
        const val SIDE = "side"
    }
}
