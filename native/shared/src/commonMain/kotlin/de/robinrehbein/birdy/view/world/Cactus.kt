package de.robinrehbein.birdy.view.world

import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.math.Vec3
import de.robinrehbein.birdy.engine.mesh.Primitives
import de.robinrehbein.birdy.engine.mesh.SPIKE_ATTRIBUTE
import de.robinrehbein.birdy.engine.mesh.bake
import de.robinrehbein.birdy.engine.mesh.reverseWinding
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import de.robinrehbein.birdy.engine.scene.ShaderPatch
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import de.robinrehbein.birdy.engine.scene.Uniform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * The grumpy spiky cactus that pops out of the lower pipe (world.js:741-850): built once and
 * baked into one geometry with a `spike` attribute, so each cactus is one draw call.
 */
internal object CactusGeometry {
    val geometry: Geometry by lazy { build() }

    private val R = Vec3(CactusLook.R_X, CactusLook.R_Y, CactusLook.R_Z)
    private val UP = Vec3(0f, 1f, 0f)
    private val FRONT = Vec3(0f, 0f, 1f)

    private fun m(color: Int) = BasicMaterial().apply { this.color.setHex(color) }

    private fun build(): Geometry {
        val root = Node("cactus")
        val body = Mesh(Primitives.sphere(1.0, 10, 7), m(CactusLook.GREEN))
        body.scale.set(R)
        body.position.y = CactusLook.Y
        root.add(body)

        // A point on the body surface in direction d (lifted along the normal) and its normal.
        fun place(mesh: Mesh, d: Vec3, lift: Float, up: Vec3 = UP): Mesh {
            val n = d.clone().divide(R).normalize()
            val pos = d.clone().multiply(R).add(0f, CactusLook.Y, 0f).addScaledVector(n, lift)
            mesh.position.set(pos)
            mesh.quaternionOverride = Quat().setFromUnitVectors(up, n)
            root.add(mesh)
            return mesh
        }

        // Spikes all around, but none on the face and none on the top (flower).
        val spikeGeo = Primitives.cone(0.05, 0.32, 4)
        val spikeMat = m(CactusLook.SPIKE)
        for (i in 0 until 46) {
            val phi = acos(1 - (2 * (i + 0.5)) / 46)
            val d = Vec3().setFromSphericalCoords(1.0, phi, i * 2.399)
            val face = d.z > 0.3f && d.y > -0.6f && abs(d.x) < 0.8f
            if (face || d.y > 0.8f || d.y < -0.6f) continue
            val spike = place(Mesh(spikeGeo, spikeMat), d.clone(), 0.1f)
            spike.spikeDir = Vec3(0f, 1f, 0f).applyQuat(spike.quaternionOverride!!)
        }
        // Face: eyes set into the body, squinting pupils, angry brows, a frown with two fangs.
        for (side in intArrayOf(-1, 1)) {
            val eye = place(Mesh(Primitives.sphere(0.15, 10, 8), m(CactusLook.EYE)), Vec3(side * 0.34f, 0.26f, 0.9f).normalize(), -0.05f, FRONT)
            eye.scale.set(1f, 1.1f, 0.6f)
            place(Mesh(Primitives.sphere(0.065, 8, 6), m(CactusLook.PUPIL)), Vec3(side * 0.3f, 0.24f, 0.92f).normalize(), 0.02f, FRONT)
            val brow = place(Mesh(Primitives.box(0.28, 0.07, 0.05), m(CactusLook.BROW)), Vec3(side * 0.32f, 0.5f, 0.8f).normalize(), -0.01f, FRONT)
            brow.rotateZ(side * 0.4f)
        }
        val mouth = place(
            Mesh(Primitives.sphere(0.2, 10, 6, 0.0, PI * 2, 0.0, PI / 2), m(CactusLook.MOUTH)),
            Vec3(0f, -0.12f, 1f).normalize(), -0.06f, FRONT,
        )
        mouth.scale.set(1f, 0.45f, 0.35f)
        for (side in intArrayOf(-1, 1)) {
            val fang = place(Mesh(Primitives.cone(0.05, 0.14, 4), m(CactusLook.TOOTH)), Vec3(side * 0.12f, -0.15f, 1f).normalize(), 0f)
            fang.position.y += 0.04f
        }
        // Flower on top.
        val top = CactusLook.Y + CactusLook.R_Y
        for (i in 0 until 6) {
            val a = (i / 6.0) * PI * 2
            val petal = Mesh(Primitives.sphere(0.13, 6, 4), m(CactusLook.PETAL))
            petal.scale.set(1.4f, 0.45f, 0.8f)
            petal.position.set((cos(a) * 0.17).toFloat(), top + 0.02f, (sin(a) * 0.17).toFloat())
            petal.rotation.y = (-a).toFloat()
            root.add(petal)
        }
        val pollen = Mesh(Primitives.sphere(0.08, 6, 4), m(CactusLook.POLLEN))
        pollen.position.y = top + 0.06f
        root.add(pollen)
        // Cartoon outline: a slightly bigger inside-out body in plum (inverted hull).
        val hull = Mesh(Primitives.sphere(1.0, 10, 7).reverseWinding(), m(CactusLook.OUTLINE))
        hull.scale.set(R).addScalar(0.07f)
        hull.position.y = CactusLook.Y
        root.add(hull)
        return bake(root, spikes = true)
    }
}

/**
 * world.js `cactusMaterial()`: one material per cactus (so each bristles on its own) sharing one
 * program; `bristle` pushes the spike tips outwards along the spike.
 */
internal class CactusMaterial {
    val bristle = Uniform.F(0f)
    val material = StandardMaterial().apply {
        vertexColors = true
        roughness = 0.55f
        flatShading = true
        patch = ShaderPatch(
            key = "cactus",
            vertexHead = "in vec3 $SPIKE_ATTRIBUTE;\nuniform float bristle;\n",
            vertexBody = "transformed += $SPIKE_ATTRIBUTE * bristle * ${WorldLook.BRISTLE_LENGTH};\n",
            uniforms = linkedMapOf("bristle" to bristle),
        )
    }
}
