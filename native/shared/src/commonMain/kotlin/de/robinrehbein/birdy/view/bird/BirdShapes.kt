package de.robinrehbein.birdy.view.bird

import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.math.Vec3
import de.robinrehbein.birdy.engine.mesh.ExtrudeOptions
import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.mesh.Shape
import de.robinrehbein.birdy.engine.mesh.rotateX
import de.robinrehbein.birdy.engine.mesh.translate
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.Material
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sqrt

/** bird.js `mat(color)`: faceted standard material, roughness 0.55. */
internal fun flatMat(color: Int, roughness: Float = 0.55f): StandardMaterial = StandardMaterial().apply {
    this.color.setHex(color)
    this.roughness = roughness
    flatShading = true
}

/** bird.js `smooth(color)`: non-faceted, roughness 0.45 (tail feathers). */
internal fun smoothMat(color: Int): StandardMaterial = StandardMaterial().apply {
    this.color.setHex(color)
    roughness = 0.45f
}

/** bird.js `at(m, x, y, z, rx, ry, rz)`. */
internal fun <T : Node> T.at(x: Double, y: Double, z: Double, rx: Double = 0.0, ry: Double = 0.0, rz: Double = 0.0): T {
    position.set(x.toFloat(), y.toFloat(), z.toFloat())
    rotation.set(rx.toFloat(), ry.toFloat(), rz.toFloat())
    return this
}

internal fun <T : Node> T.scaled(x: Double, y: Double, z: Double): T {
    scale.set(x.toFloat(), y.toFloat(), z.toFloat())
    return this
}

internal fun group(vararg children: Node): Node = Node().apply { add(*children) }

internal object BirdShapes {
    /** Body ellipsoid radii (sphere 0.6 scaled 1, 0.9, 1.15). */
    val BODY = Vec3(0.6f, 0.54f, 0.69f)
    private val UP = Vec3(0f, 1f, 0f)
    private val FORWARD = Vec3(0f, 0f, 1f)

    /** Feather wing outline extruded with a bevel, laid flat (bird.js `createWingGeometry`). */
    fun wingGeometry(): Geometry {
        val shape = Shape()
        shape.moveTo(0.0, 0.26)
        shape.bezierCurveTo(0.3, 0.38, 0.7, 0.28, 0.9, 0.04)
        shape.quadraticCurveTo(0.9, -0.16, 0.72, -0.13)
        shape.quadraticCurveTo(0.66, -0.33, 0.48, -0.24)
        shape.quadraticCurveTo(0.39, -0.4, 0.22, -0.28)
        shape.quadraticCurveTo(0.08, -0.32, 0.0, -0.16)
        shape.lineTo(0.0, 0.26)
        val geo = Primitives.extrude(
            shape,
            ExtrudeOptions(
                depth = 0.04, bevelEnabled = true, bevelThickness = 0.035, bevelSize = 0.03,
                bevelSegments = 2, curveSegments = 5,
            ),
        )
        geo.translate(0.0, 0.0, -0.02)
        geo.rotateX(-PI / 2)
        return geo
    }

    fun heartShape(s: Double): Shape = Shape().apply {
        moveTo(0.0, -0.9 * s)
        bezierCurveTo(-1.3 * s, 0.0, -0.6 * s, 1 * s, 0.0, 0.45 * s)
        bezierCurveTo(0.6 * s, 1 * s, 1.3 * s, 0.0, 0.0, -0.9 * s)
    }

    fun heartGeometry(s: Double): Geometry =
        Primitives.extrude(heartShape(s), ExtrudeOptions(depth = 0.03, bevelEnabled = false))

    /** bird.js `disc(r)`: a flat cylinder (axis Y). */
    fun disc(r: Double): Geometry = Primitives.cylinder(r, r, 0.03, 10)

    fun sphere(r: Double, material: Material, sx: Double = 1.0, sy: Double = sx, sz: Double = sx): Mesh =
        Mesh(Primitives.sphere(r, 8, 6), material).scaled(sx, sy, sz)

    /** A flat piece lying on the body surface in direction [dir], facing along the surface normal. */
    fun decal(geo: Geometry, material: Material, dir: Vec3, lift: Float = 0.005f): Mesh {
        val d = dir.clone().normalize()
        val p = d.clone().multiply(BODY)
        val n = d.clone().divide(BODY).normalize()
        return Mesh(geo, material).apply {
            position.set(p).addScaledVector(n, lift)
            quaternionOverride = Quat().setFromUnitVectors(UP, n)
        }
    }

    /** A ring around the body at depth [z] (a slice of the ellipsoid). */
    fun bodyRing(material: Material, z: Double, tube: Double, arc: Double = PI * 2, y: Double = 0.0): Mesh {
        val bz = BODY.z.toDouble(); val by = BODY.y.toDouble()
        val f = sqrt(max(0.0, 1 - (z / bz) * (z / bz) - (y / by) * (y / by)))
        return Mesh(Primitives.torus(1.0, tube, 4, 18, arc), material).apply {
            scale.set((BODY.x * f + 0.01).toFloat(), (by * f + 0.01).toFloat(), 1f)
            position.set(0f, y.toFloat(), z.toFloat())
        }
    }

    private class Eye(val side: Int) {
        val pos = Vec3(side * 0.38f, 0.25f, -0.38f)
        val dir = Vec3(side * 0.7f, 0.1f, -0.7f).normalize()
    }

    private val EYES = listOf(Eye(-1), Eye(1))

    /** Something placed in front of each eye (lens, frame), local +Z facing outwards. */
    fun overEyes(dist: Float = 0.2f, make: (side: Int) -> Node): Node {
        val g = Node()
        for (e in EYES) {
            val m = make(e.side)
            m.position.set(e.pos).addScaledVector(e.dir, dist)
            m.quaternionOverride = Quat().setFromUnitVectors(FORWARD, e.dir)
            g.add(m)
        }
        return g
    }
}
