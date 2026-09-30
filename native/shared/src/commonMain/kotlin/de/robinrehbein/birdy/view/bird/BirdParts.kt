package de.robinrehbein.birdy.view.bird

import de.robinrehbein.birdy.engine.math.Vec3
import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.mesh.rotateX
import de.robinrehbein.birdy.engine.scene.Material
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import de.robinrehbein.birdy.engine.scene.Side
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import de.robinrehbein.birdy.meta.Kind
import de.robinrehbein.birdy.view.bird.BirdShapes.bodyRing
import de.robinrehbein.birdy.view.bird.BirdShapes.decal
import de.robinrehbein.birdy.view.bird.BirdShapes.disc
import de.robinrehbein.birdy.view.bird.BirdShapes.overEyes
import de.robinrehbein.birdy.view.bird.BirdShapes.sphere
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Workshop parts per category, keyed by catalog id (bird.js `buildParts`). */
internal class BirdParts(
    val pattern: Map<String, Node>,
    val hat: Map<String, Node>,
    val eyes: Map<String, Node>,
    val beak: Map<String, Node>,
    /** `hat.cap.userData.propeller`, spun by `animateWings`. */
    val propeller: Node,
    /** `hat.halo.userData.ring`, bobbed by `animateWings`. */
    val haloRing: Mesh,
) {
    fun of(kind: Kind): Map<String, Node> = when (kind) {
        Kind.Pattern -> pattern
        Kind.Hat -> hat
        Kind.Eyes -> eyes
        Kind.Beak -> beak
        else -> emptyMap()
    }

    companion object {
        fun build(beakMat: Material, beakLowMat: Material, patDark: Material, patLight: Material, roundBeak: Node): BirdParts {
            val dark = flatMat(0x2e2530)
            val pink = flatMat(0xff8fb0)
            val red = flatMat(0xff3d6e)
            val gold = StandardMaterial().apply {
                color.setHex(0xffc629); roughness = 0.3f; metalness = 0.6f; flatShading = true
            }

            // Patterns
            val pattern = LinkedHashMap<String, Node>()
            pattern["plain"] = Node()
            pattern["cheeks"] = group(*listOf(-1, 1).map { s -> decal(disc(0.1), pink, Vec3(s * 0.75f, 0.02f, -0.66f)) }.toTypedArray())
            pattern["spots"] = group(
                *listOf(
                    doubleArrayOf(0.3, 0.8, 0.4, 0.1), doubleArrayOf(-0.35, 0.75, 0.45, 0.12),
                    doubleArrayOf(0.0, 0.5, 0.85, 0.11), doubleArrayOf(0.55, 0.4, 0.6, 0.09),
                    doubleArrayOf(-0.6, 0.35, 0.6, 0.1), doubleArrayOf(0.1, 0.95, -0.05, 0.09),
                    doubleArrayOf(-0.2, 0.15, 0.95, 0.09), doubleArrayOf(0.35, 0.1, 0.9, 0.08),
                ).map { (x, y, z, r) -> decal(disc(r), patLight, Vec3(x.toFloat(), y.toFloat(), z.toFloat())) }.toTypedArray(),
            )
            pattern["stripes"] = group(*listOf(0.12, 0.34, 0.54).map { z -> bodyRing(patDark, z, 0.05, PI) }.toTypedArray())
            val band = bodyRing(dark, 0.0, 0.13, PI * 2)
            band.rotation.x = (PI / 2).toFloat()
            band.scaled(0.54, 0.62, 0.6)
            band.position.set(0f, 0.26f, -0.02f)
            pattern["mask"] = group(
                band,
                sphere(0.08, dark, 1.0, 0.7, 1.4).at(0.1, 0.22, 0.66, 0.6, 0.4),
                sphere(0.08, dark, 1.0, 0.7, 1.4).at(-0.1, 0.22, 0.66, 0.6, -0.4),
            )
            val heart = decal(BirdShapes.heartGeometry(0.3), red, Vec3(0f, 0.5f, 1f), 0.01f)
            heart.geometry.rotateX(-PI / 2)
            pattern["heart"] = group(heart)

            // Hats (anchored on the top of the head)
            val hat = LinkedHashMap<String, Node>()
            hat["none"] = Node()
            fun head() = Node().at(0.0, 0.5, -0.12, 0.12)
            hat["crest"] = head().apply {
                for ((rz, z) in listOf(-0.35 to 0.12, 0.0 to 0.0, 0.35 to 0.12)) {
                    add(sphere(0.1, patDark, 1.0, 3.0, 1.0).at(0.0, 0.2, z, -0.35, 0.0, rz))
                }
            }
            val flower = Node().at(0.24, 0.02, -0.05, 0.0, 0.0, -0.6)
            for (i in 0 until 5) {
                val a = i / 5.0 * PI * 2
                flower.add(sphere(0.08, flatMat(0xffffff), 1.0, 0.5, 1.0).at(cos(a) * 0.1, 0.02, sin(a) * 0.1))
            }
            flower.add(sphere(0.06, flatMat(0xffc93c)).at(0.0, 0.05, 0.0))
            hat["flower"] = head().apply { add(flower) }
            hat["party"] = head().apply {
                add(
                    Mesh(Primitives.cone(0.2, 0.5, 8), flatMat(0x5ad1ff)).at(0.0, 0.22, 0.0, 0.0, 0.0, 0.15),
                    bodyRing(flatMat(0xffd84a), 0.0, 0.03).at(-0.02, 0.08, 0.0, PI / 2).scaled(0.19, 0.19, 1.0),
                    sphere(0.07, flatMat(0xff5a8a)).at(-0.07, 0.47, 0.0),
                )
            }
            val propeller = Node().at(0.0, 0.4, 0.0)
            propeller.add(
                sphere(0.06, flatMat(0xffd84a), 3.2, 0.35, 1.0).at(0.17, 0.0, 0.0),
                sphere(0.06, flatMat(0x5ad1ff), 3.2, 0.35, 1.0).at(-0.17, 0.0, 0.0),
            )
            hat["cap"] = head().apply {
                add(
                    Mesh(Primitives.sphere(0.3, 10, 5, 0.0, PI * 2, 0.0, PI / 2), flatMat(0xe8453c)).at(0.0, -0.02, 0.0),
                    Mesh(Primitives.cylinder(0.2, 0.2, 0.03, 10, 1, false, -PI / 2, PI), flatMat(0x2f86d0))
                        .at(0.0, -0.02, -0.18, 0.0, PI / 2),
                    Mesh(Primitives.cylinder(0.02, 0.02, 0.14, 5), flatMat(0x555555)).at(0.0, 0.33, 0.0),
                    propeller,
                )
            }
            hat["tophat"] = head().apply {
                add(
                    Mesh(Primitives.cylinder(0.36, 0.36, 0.04, 12), dark).at(0.0, 0.0, 0.0),
                    Mesh(Primitives.cylinder(0.22, 0.24, 0.42, 12), dark).at(0.0, 0.22, 0.0),
                    Mesh(Primitives.cylinder(0.245, 0.245, 0.08, 12), red).at(0.0, 0.07, 0.0),
                )
            }
            hat["viking"] = head().apply {
                add(
                    Mesh(Primitives.sphere(0.36, 10, 5, 0.0, PI * 2, 0.0, PI / 2), flatMat(0xa9b3bd)).at(0.0, -0.1, 0.0),
                    Mesh(Primitives.cylinder(0.37, 0.37, 0.07, 12), gold).at(0.0, -0.08, 0.0),
                )
                for (s in listOf(-1.0, 1.0)) {
                    add(Mesh(Primitives.cone(0.07, 0.36, 6), flatMat(0xfff3d6)).at(s * 0.38, 0.12, 0.0, 0.0, 0.0, -s * 0.8))
                }
            }
            val crownBand = StandardMaterial().apply {
                color.setHex(0xffc629); roughness = 0.3f; metalness = 0.6f; flatShading = true; side = Side.Double
            }
            hat["crown"] = head().apply {
                add(Mesh(Primitives.cylinder(0.24, 0.22, 0.14, 10, 1, true), crownBand).at(0.0, 0.02, 0.0))
                val blue = flatMat(0x4ab8ff)
                for (i in 0 until 5) {
                    val a = i / 5.0 * PI * 2
                    add(Mesh(Primitives.cone(0.06, 0.14, 4), gold).at(cos(a) * 0.22, 0.15, sin(a) * 0.22))
                    add(sphere(0.035, if (i % 2 == 1) red else blue).at(cos(a) * 0.235, 0.02, sin(a) * 0.235))
                }
            }
            val haloMat = StandardMaterial().apply {
                color.setHex(0xffe066); emissive.setHex(0xffc629); emissiveIntensity = 0.6f; flatShading = true
                userData[OWN_GLOW] = true
            }
            val ring = Mesh(Primitives.torus(0.26, 0.045, 6, 20), haloMat).at(0.0, 0.34, 0.0, PI / 2)
            hat["halo"] = head().apply { add(ring) }

            // Eyes
            val eyes = LinkedHashMap<String, Node>()
            eyes["normal"] = Node()
            eyes["lashes"] = overEyes(0.05f) { side ->
                group(*listOf(-0.1, 0.0, 0.1).map { x ->
                    Mesh(Primitives.box(0.03f, 0.12f, 0.03f), dark).at(x, 0.2, -0.02, 0.0, 0.0, -x * 2.5 * side)
                }.toTypedArray())
            }
            eyes["brows"] = overEyes(0.08f) { side ->
                Mesh(Primitives.box(0.3f, 0.06f, 0.06f), dark).at(0.0, 0.23, 0.0, 0.0, 0.0, side * -0.4)
            }
            fun discLens(material: Material) = Mesh(disc(0.19).rotateX(PI / 2), material)
            fun bridge(material: Material) = Mesh(Primitives.box(0.42f, 0.04f, 0.04f), material).at(0.0, 0.32, -0.62)
            val shadesMat = StandardMaterial().apply {
                color.setHex(0x1e1e28); roughness = 0.15f; metalness = 0.3f; flatShading = true
            }
            eyes["shades"] = overEyes { discLens(shadesMat) }.apply { add(bridge(dark)) }
            eyes["hearts"] = overEyes { Mesh(BirdShapes.heartGeometry(0.17), red) }.apply { add(bridge(red)) }
            val strap = bodyRing(flatMat(0x7a4a24), 0.0, 0.06)
            strap.rotation.x = (PI / 2).toFloat()
            strap.scaled(0.53, 0.62, 0.6)
            strap.position.set(0f, 0.3f, 0f)
            eyes["goggles"] = overEyes {
                group(
                    Mesh(Primitives.torus(0.19, 0.05, 5, 14), flatMat(0x7a4a24)),
                    Mesh(disc(0.17).rotateX(PI / 2), flatMat(0xbfe9ff)),
                )
            }.apply { add(strap) }

            // Beaks (round = the default built by BirdRig)
            val beak = LinkedHashMap<String, Node>()
            beak["round"] = roundBeak
            beak["duck"] = group(
                sphere(0.26, beakMat, 1.45, 0.34, 1.5).at(0.0, -0.03, -0.74),
                sphere(0.24, beakLowMat, 1.3, 0.3, 1.3).at(0.0, -0.13, -0.68),
            )
            beak["hook"] = BeakShapes.eagle(beakMat, beakLowMat)
            beak["toucan"] = BeakShapes.toucan()
            return BirdParts(pattern, hat, eyes, beak, propeller, ring)
        }

        /** `material.userData.ownGlow`: excluded from the group-wide `setGlow`. */
        const val OWN_GLOW = "ownGlow"
    }
}
