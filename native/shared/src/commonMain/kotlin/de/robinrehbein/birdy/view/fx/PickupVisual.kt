package de.robinrehbein.birdy.view.fx

import de.robinrehbein.birdy.engine.math.MathUtil
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.mesh.ExtrudeOptions
import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.mesh.Shape
import de.robinrehbein.birdy.engine.mesh.center
import de.robinrehbein.birdy.engine.mesh.mergeGeometries
import de.robinrehbein.birdy.engine.mesh.setVertexColor
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import de.robinrehbein.birdy.game.PowerType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One power-up pickup: spinning icon inside a translucent bubble with a billboard rim
 * (powerups.js `createPowerupPickup` / `animatePickup`).
 */
class PickupVisual(val type: PowerType, rimGeometry: Geometry = sharedRim()) {
    private var rainbow: StandardMaterial? = null
    val icon: Node = when (type) {
        PowerType.Star -> buildStar()
        PowerType.Magnet -> buildMagnet()
        PowerType.Mini -> buildMushroom()
    }.apply { scale.setScalar(1.7f) }
    val bubble = Mesh(
        Primitives.sphere(1.15, 16, 12),
        StandardMaterial().apply {
            color.setHex(type.color); transparent = true; opacity = 0.3f; roughness = 0.1f; depthWrite = false
            emissive.setHex(type.color); emissiveIntensity = 0.35f
        },
        "bubble",
    )
    val rim = Mesh(
        rimGeometry,
        BasicMaterial().apply { vertexColors = true; transparent = true; opacity = 0.9f; depthWrite = false },
        "rim",
    ).apply { quaternionOverride = Quat() }
    val group = Node("pickup-${type.id}").apply {
        add(icon, bubble, rim)
        visible = false
    }

    /** `animatePickup(p, time, camera)`: icon spin/bob, rim billboard, star hue cycle. */
    fun animate(time: Double, cameraQuat: Quat?) {
        if (cameraQuat != null) rim.quaternionOverride!!.set(cameraQuat)
        icon.rotation.y = iconRotationY(time).toFloat()
        icon.position.y = iconBobY(time).toFloat()
        rainbow?.let {
            it.color.setHSL(rainbowHue(time), 1.0, 0.55)
            it.emissive.setHSL(rainbowHue(time), 1.0, 0.2)
        }
    }

    private fun mat(color: Int, metalness: Float = 0f) = StandardMaterial().apply {
        this.color.setHex(color); roughness = 0.4f; flatShading = true; this.metalness = metalness
    }

    private fun buildStar(): Node {
        val geo = Primitives.extrude(
            starShape(0.5, 0.22),
            ExtrudeOptions(depth = 0.14, bevelEnabled = true, bevelThickness = 0.06, bevelSize = 0.05, bevelSegments = 1),
        ).center()
        val m = mat(0xffd400).apply { emissive.setHex(0x664400) }
        rainbow = m
        return Mesh(geo, m, "star")
    }

    private fun buildMagnet(): Node {
        val g = Node("magnet")
        val red = mat(0xe53935)
        val metal = mat(0xd9d9d9, metalness = 0.6f)
        g.add(Mesh(Primitives.torus(0.3, 0.12, 6, 12, PI), red).apply { position.y = 0.05f })
        for (side in listOf(-1f, 1f)) {
            g.add(Mesh(Primitives.cylinder(0.12, 0.12, 0.22, 6), red).apply { position.set(side * 0.3f, -0.06f, 0f) })
            g.add(Mesh(Primitives.cylinder(0.12, 0.12, 0.14, 6), metal).apply { position.set(side * 0.3f, -0.24f, 0f) })
        }
        return g
    }

    private fun buildMushroom(): Node {
        val g = Node("mushroom")
        g.add(Mesh(Primitives.sphere(0.42, 10, 6, 0.0, PI * 2, 0.0, PI / 2), mat(0x9b59b6)).apply {
            position.y = 0.02f
            scale.y = 0.85f
        })
        val dotMat = mat(0xffffff)
        for ((theta, phi) in MUSHROOM_DOTS) {
            val dot = Mesh(Primitives.sphere(0.09, 5, 4), dotMat)
            dot.position.setFromSphericalCoords(0.4, phi, theta)
            dot.position.y = dot.position.y * 0.85f + 0.02f
            g.add(dot)
        }
        g.add(Mesh(Primitives.cylinder(0.18, 0.22, 0.34, 8), mat(0xfff3d6)).apply { position.y = -0.15f })
        val eyeMat = mat(0x222222)
        for (side in listOf(-1f, 1f)) {
            g.add(Mesh(Primitives.sphere(0.04, 4, 3), eyeMat).apply { position.set(side * 0.07f, -0.12f, 0.2f) })
        }
        return g
    }

    companion object {
        /** `[theta, phi]` of the five cap dots. */
        private val MUSHROOM_DOTS = listOf(0.0 to 0.3, 1.3 to 1.0, 2.8 to 0.9, 4.4 to 1.0, 5.6 to 0.95)

        fun iconRotationY(time: Double): Double = time * 2.5
        fun iconBobY(time: Double): Double = sin(time * 3) * 0.12
        fun rainbowHue(time: Double): Double = (time * 0.5) % 1

        /** Forward shrink before the pickup passes the bird (main.js, same formula as coins). */
        fun shrink(z: Double): Double = MathUtil.clamp(1 - (z - 1.5) / 1.5, 0.0, 1.0)

        fun starShape(outer: Double, inner: Double): Shape {
            val shape = Shape()
            for (i in 0 until 10) {
                val r = if (i % 2 == 1) inner else outer
                val a = i / 10.0 * PI * 2 + PI / 2
                if (i == 0) shape.moveTo(cos(a) * r, sin(a) * r) else shape.lineTo(cos(a) * r, sin(a) * r)
            }
            shape.closePath()
            return shape
        }

        /** White ring with a plum edge, vertex coloured, merged into one geometry. */
        fun sharedRim(): Geometry = mergeGeometries(
            listOf(
                Primitives.ring(1.14, 1.32, 32).setVertexColor(0xffffff),
                Primitives.ring(1.32, 1.4, 32).setVertexColor(0x543847),
            ),
        )
    }
}
