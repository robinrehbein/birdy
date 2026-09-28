package de.robinrehbein.birdy.engine.gl

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.DirectionalLight
import de.robinrehbein.birdy.engine.scene.Fog
import de.robinrehbein.birdy.engine.scene.HemisphereLight
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.test.Test

/**
 * A Birdy-lit reference scene (world.js lights, fog and shadow camera; pipe/bird/coin-like
 * materials) checked against pixels of the same scene rendered by three.js r186.
 */
class ThreeParitySceneTest {
    @Test
    fun birdyLitScene() = GlFixture(128, 128).use { f ->
        val s = Scene().apply {
            background = Color.hex(0xbfe6ff)
            fog = Fog(0xbfe6ff, 7f, 20f)
        }
        val sun = DirectionalLight(0xfff4d6, 2.2f).apply {
            position.set(12f, 30f, 10f); target.set(0f, 0f, -15f); castShadow = true
            shadow.mapSize = 1024
            shadow.left = -18f; shadow.right = 18f; shadow.top = 40f; shadow.bottom = -30f
            shadow.near = 1f; shadow.far = 90f; shadow.bias = -0.0005f
        }
        s.add(HemisphereLight(0xdff6ff, 0x6a8f3a, 1.4f), sun)
        s.add(Mesh(TestGeo.quad(20f, 20f), StandardMaterial().apply { color.setHex(0x7cc05a); roughness = 1f }).apply {
            rotation.x = (-PI / 2).toFloat(); receiveShadow = true
        })
        s.add(Mesh(TestGeo.sphere(1f, 32, 16), StandardMaterial().apply { color.setHex(0xf7d23e); roughness = 0.45f }).apply {
            position.set(-1.5f, 1f, 0f); castShadow = true
        })
        s.add(Mesh(TestGeo.box(1.2f, 1.2f, 1.2f), StandardMaterial().apply { color.setHex(0x4ea83a); roughness = 0.3f; flatShading = true }).apply {
            position.set(1.5f, 0.6f, 0.5f); rotation.y = 0.5f; castShadow = true; receiveShadow = true
        })
        s.add(Mesh(TestGeo.box(0.8f, 0.8f, 0.2f), StandardMaterial().apply {
            color.setHex(0xffcf33); emissive.setHex(0xb07800); emissiveIntensity = 0.55f
            metalness = 0.15f; roughness = 0.35f; flatShading = true
        }).apply { position.set(0f, 2.5f, -2f); rotation.y = 0.6f })
        s.add(Mesh(TestGeo.quad(1.4f, 1.4f), BasicMaterial().apply {
            color.setHex(0x000000); transparent = true; opacity = 0.22f; depthWrite = false
        }).apply { rotation.x = (-PI / 2).toFloat(); position.set(0f, 0.03f, 2f) })
        s.add(Mesh(TestGeo.box(2f, 3f, 2f), StandardMaterial().apply { color.setHex(0xd9534f); roughness = 0.6f }).apply {
            position.set(0f, 1.5f, -10f)
        })
        val cam = f.camera(60f).apply { position.set(0f, 4f, 8f); rotation.x = -atan2(3f, 8f) }
        val px = f.render(s, cam).save("parity_native")
        // Pixels sampled from the identical three.js r186 scene (Chromium + SwiftShader, 128x128,
        // antialias off). Whole-image mean abs difference was 0.02 per channel; only a handful of
        // silhouette pixels differ (rasterizer tie-breaking).
        val threeJs = mapOf(
            "sky" to Triple(64, 2, 0xbfe6ff),
            "sphere lit" to Triple(36, 66, 0xc2a92e),
            "sphere shade" to Triple(44, 72, 0xaa9626),
            "box side" to Triple(84, 70, 0x3f8c2a),
            "box top" to Triple(84, 62, 0x489a36),
            "coin" to Triple(64, 40, 0xe1ba3c),
            "ground" to Triple(20, 110, 0x73b452),
            "ground in shadow" to Triple(64, 90, 0x5a8c40),
        )
        for ((what, p) in threeJs) assertColor(p.third, px[p.first, p.second], 2, what)
    }
}
