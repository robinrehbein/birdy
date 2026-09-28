package de.robinrehbein.birdy.engine.mesh

import de.robinrehbein.birdy.engine.math.Mat4
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.math.Vec3
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Kotlin twins of the cases in scripts/native-golden/engine-geometry.mjs. */
internal object GeometryCases {
    fun starShape(points: Int, outer: Double, inner: Double, close: Boolean): Shape {
        val s = Shape()
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 1) inner else outer
            val a = (i.toDouble() / (points * 2)) * PI * 2 + PI / 2
            if (i == 0) s.moveTo(cos(a) * r, sin(a) * r) else s.lineTo(cos(a) * r, sin(a) * r)
        }
        if (close) s.closePath()
        return s
    }

    fun heartShape(s: Double): Shape = Shape().apply {
        moveTo(0.0, -0.9 * s)
        bezierCurveTo(-1.3 * s, 0.0, -0.6 * s, 1 * s, 0.0, 0.45 * s)
        bezierCurveTo(0.6 * s, 1 * s, 1.3 * s, 0.0, 0.0, -0.9 * s)
    }

    fun wingGeometry(): Geometry {
        val shape = Shape().apply {
            moveTo(0.0, 0.26)
            bezierCurveTo(0.3, 0.38, 0.7, 0.28, 0.9, 0.04)
            quadraticCurveTo(0.9, -0.16, 0.72, -0.13)
            quadraticCurveTo(0.66, -0.33, 0.48, -0.24)
            quadraticCurveTo(0.39, -0.4, 0.22, -0.28)
            quadraticCurveTo(0.08, -0.32, 0.0, -0.16)
            lineTo(0.0, 0.26)
        }
        return Primitives.extrude(
            shape,
            ExtrudeOptions(depth = 0.04, bevelEnabled = true, bevelThickness = 0.035, bevelSize = 0.03, bevelSegments = 2, curveSegments = 5),
        ).translate(0.0, 0.0, -0.02).rotateX(-PI / 2)
    }

    fun holeShape(): Shape = Shape().apply {
        moveTo(-1.0, -1.0); lineTo(1.0, -1.0); lineTo(1.0, 1.0); lineTo(-1.0, 1.0); lineTo(-1.0, -1.0)
        holes.add(Path().apply { absarc(0.1, 0.0, 0.4, 0.1, 0.1 + PI * 2, true) })
    }

    private fun basic(hex: Int) = BasicMaterial().apply { color.setHex(hex) }

    fun bakeTwo(): Node {
        val root = Node()
        root.position.set(1f, 2f, 3f)
        root.rotation.set(0.1f, 0.2f, 0.3f)
        root.scale.setScalar(2f)
        val a = Mesh(Primitives.box(1.0, 2.0, 3.0), basic(0xff8800))
        a.position.set(0.5f, 0f, 0f)
        a.rotation.set(0f, 0.7f, 0f)
        a.scale.set(1f, 2f, 1f)
        val sub = Node()
        sub.position.set(0f, 1f, 0f)
        sub.rotation.z = 0.4f
        val b = Mesh(Primitives.cone(0.05, 0.32, 4), basic(0xfff3d6))
        val n = Vec3(0.3f, 0.8f, 0.52f).normalize()
        b.position.set(0.2f, 0.1f, -0.3f)
        b.setQuaternion(Quat().setFromUnitVectors(Vec3(0f, 1f, 0f), n))
        b.spikeDir = Vec3(0f, 1f, 0f).applyQuat(b.getQuaternion())
        sub.add(b)
        root.add(a, sub)
        return root
    }

    fun coinGroup(): Node {
        val root = Node()
        val face = Mesh(Primitives.cylinder(0.4, 0.4, 0.1, 16), basic(0xffcf33))
        face.rotation.x = (PI / 2).toFloat()
        val rim = Mesh(Primitives.torus(0.41, 0.055, 4, 16), basic(0xf2a100))
        val starGeo = Primitives.extrude(starShape(5, 0.21, 0.09, false), ExtrudeOptions(depth = 0.03, bevelEnabled = false))
        val front = Mesh(starGeo, basic(0xfff0a0))
        front.position.z = 0.04f
        val back = Mesh(starGeo, basic(0xfff0a0))
        back.position.z = -0.04f
        back.rotation.y = PI.toFloat()
        root.add(face, rim, front, back)
        return root
    }

    val cases: Map<String, () -> Geometry> = mapOf(
        "box.default" to { Primitives.box() },
        "box.stripe" to { Primitives.box(0.28, 1.0, 0.28) },
        "box.segments" to { Primitives.box(2.0, 3.0, 4.0, 2, 3, 1) },
        "plane.track" to { Primitives.plane(26.0, 400.0) },
        "plane.segments" to { Primitives.plane(2.0, 3.0, 2, 3) },
        "sphere.body" to { Primitives.sphere(0.6, 14, 10) },
        "sphere.cactus" to { Primitives.sphere(1.0, 10, 7) },
        "sphere.mouth" to { Primitives.sphere(0.2, 10, 6, 0.0, PI * 2, 0.0, PI / 2) },
        "sphere.tiny" to { Primitives.sphere(0.04, 4, 3) },
        "sphere.sky" to { Primitives.sphere(300.0, 24, 12) },
        "sphere.band" to { Primitives.sphere(1.0, 8, 6, 0.3, PI, PI / 4, PI / 2) },
        "cylinder.trunk" to { Primitives.cylinder(0.3, 0.4, 2.0, 6) },
        "cylinder.pipe" to { Primitives.cylinder(1.1, 1.1, 1.0, 16) },
        "cylinder.brim" to { Primitives.cylinder(0.2, 0.2, 0.03, 10, 1, false, -PI / 2, PI) },
        "cylinder.crown" to { Primitives.cylinder(0.24, 0.22, 0.14, 10, 1, true) },
        "cylinder.segments" to { Primitives.cylinder(1.0, 0.5, 2.0, 5, 3) },
        "cone.spike" to { Primitives.cone(0.05, 0.32, 4) },
        "cone.pine" to { Primitives.cone(1.5, 3.2, 7) },
        "cone.default" to { Primitives.cone() },
        "torus.coinRim" to { Primitives.torus(0.41, 0.055, 4, 16) },
        "torus.bodyRingHalf" to { Primitives.torus(1.0, 0.05, 4, 18, PI) },
        "torus.halo" to { Primitives.torus(0.26, 0.045, 6, 20) },
        "torus.default" to { Primitives.torus() },
        "icosahedron.leaf" to { Primitives.icosahedron(1.8, 0) },
        "icosahedron.hill" to { Primitives.icosahedron(1.0, 1) },
        "icosahedron.detail2" to { Primitives.icosahedron(0.5, 2) },
        "dodecahedron.rock" to { Primitives.dodecahedron(0.9, 0) },
        "dodecahedron.detail1" to { Primitives.dodecahedron(1.0, 1) },
        "circle.shadow" to { Primitives.circle(0.7, 20) },
        "circle.arc" to { Primitives.circle(1.0, 5, 0.5, PI) },
        "ring.marker" to { Primitives.ring(0.2, 0.36, 20) },
        "ring.aura" to { Primitives.ring(0.82, 1.0, 40) },
        "ring.segments" to { Primitives.ring(0.5, 1.0, 8, 3, 0.2, PI) },
        "shape.bonkStar" to { Primitives.shape(starShape(8, 1.25, 0.62, false)) },
        "shape.heart" to { Primitives.shape(heartShape(0.3)) },
        "shape.hole" to { Primitives.shape(holeShape(), 6) },
        "extrude.wing" to { wingGeometry() },
        "extrude.powerupStar" to {
            Primitives.extrude(
                starShape(5, 0.5, 0.22, true),
                ExtrudeOptions(depth = 0.14, bevelEnabled = true, bevelThickness = 0.06, bevelSize = 0.05, bevelSegments = 1),
            ).center()
        },
        "extrude.coinStar" to { Primitives.extrude(starShape(5, 0.21, 0.09, false), ExtrudeOptions(depth = 0.03, bevelEnabled = false)) },
        "extrude.heart" to { Primitives.extrude(heartShape(0.3), ExtrudeOptions(depth = 0.03, bevelEnabled = false)).rotateX(-PI / 2) },
        "extrude.hole" to { Primitives.extrude(holeShape(), ExtrudeOptions(depth = 0.5, bevelSegments = 2, steps = 2, curveSegments = 6)) },
        "extrude.defaults" to { Primitives.extrude(starShape(4, 1.0, 0.5, true)) },
        "ops.pipeTranslated" to { Primitives.cylinder(1.1, 1.1, 1.0, 16).translate(0.0, 0.5, 0.0) },
        "ops.discRotated" to { Primitives.cylinder(0.19, 0.19, 0.03, 10).rotateX(PI / 2) },
        "ops.scaledRotZ" to { Primitives.sphere(1.0, 6, 4).scale(1.0, 2.0, 3.0).rotateZ(0.5).rotateY(-0.25) },
        "ops.nonIndexedSphere" to { Primitives.sphere(0.6, 14, 10).toNonIndexed() },
        "ops.smoothNormals" to { Primitives.cylinder(0.3, 0.4, 2.0, 6).computeVertexNormals() },
        "ops.flatNormals" to { Primitives.sphere(0.5, 5, 4).toNonIndexed().computeVertexNormals() },
        "ops.applyMatrix" to {
            val m = Mat4().compose(Vec3(1f, -2f, 0.5f), Quat().setFromEulerXYZ(0.3, -0.6, 1.1), Vec3(1.5f, 0.5f, -2f))
            Primitives.box(1.0, 2.0, 3.0).applyMat4(m)
        },
        "merge.rim" to {
            mergeGeometries(listOf(Primitives.ring(1.14, 1.32, 32).setVertexColor(0xffffff), Primitives.ring(1.32, 1.4, 32).setVertexColor(0x543847)))
        },
        "bake.two" to { bake(bakeTwo(), spikes = true) },
        "bake.coin" to { bake(coinGroup()) },
    )
}
