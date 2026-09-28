package de.robinrehbein.birdy.engine.gl

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.Blending
import de.robinrehbein.birdy.engine.scene.CustomShaderMaterial
import de.robinrehbein.birdy.engine.scene.DirectionalLight
import de.robinrehbein.birdy.engine.scene.Fog
import de.robinrehbein.birdy.engine.scene.HemisphereLight
import de.robinrehbein.birdy.engine.scene.InstanceData
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.engine.scene.ShaderPatch
import de.robinrehbein.birdy.engine.scene.Side
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import de.robinrehbein.birdy.engine.scene.Texture
import de.robinrehbein.birdy.engine.scene.Uniform
import de.robinrehbein.birdy.engine.scene.VertexAttribute
import de.robinrehbein.birdy.engine.math.Mat4
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shader test gallery: small scenes rendered through the real GLSL ES 3.00 pipeline on Mesa
 * llvmpipe, checked against three.js formulas. Debug images go to native/build/gltest/.
 */
class ShaderGalleryTest {
    private fun scene(bg: Int = 0x000000) = Scene().apply { background = Color.hex(bg) }

    @Test
    fun clearUsesBackgroundHexExactly() = GlFixture().use { f ->
        val px = f.render(scene(0x87ceeb), f.camera()).save("clear")
        assertEquals(0xff87ceeb.toInt(), px[0, 0])
        assertEquals(0xff87ceeb.toInt(), px[63, 63])
    }

    @Test
    fun unlitColourRoundTripsSrgb() = GlFixture().use { f ->
        val s = scene()
        for ((i, hex) in listOf(0xff0000, 0x4080c0, 0x123456).withIndex()) {
            s.clear()
            s.add(Mesh(TestGeo.quad(4f, 4f), BasicMaterial().apply { color.setHex(hex) }).apply { position.set(0f, 0f, -3f) })
            val px = f.render(s, f.camera()).save("unlit_$i")
            assertColor(hex, px.center(), 1, "unlit $i")
        }
    }

    @Test
    fun standardMaterialMatchesThreeJsFormula() = GlFixture().use { f ->
        // Facing quad (n = v = +Z), sun at 30 degrees in XZ, hemisphere up.
        val theta = 30.0 * PI / 180
        for ((i, params) in listOf(0.4f to 0f, 1f to 0f, 0.3f to 0.15f, 0.55f to 0.6f).withIndex()) {
            val (rough, metal) = params
            val s = scene()
            val hemi = HemisphereLight(0xdff6ff, 0x6a8f3a, 1.4f)
            val sun = DirectionalLight(0xfff4d6, 2.2f).apply {
                position.set((10 * sin(theta)).toFloat(), 0f, (10 * cos(theta)).toFloat())
            }
            val mat = StandardMaterial().apply { color.setHex(0xf7d23e); roughness = rough; metalness = metal }
            s.add(hemi, sun, Mesh(TestGeo.quad(4f, 4f), mat).apply { position.set(0f, 0f, -3f) })
            val px = f.render(s, f.camera()).save("standard_$i")

            val n = RefLighting.V(0.0, 0.0, 1.0)
            val l = RefLighting.V(sin(theta), 0.0, cos(theta))
            val up = RefLighting.V(0.0, 1.0, 0.0)
            fun ch(c: (Color) -> Float): Double = RefLighting.standard(
                n, n, l, up,
                diffuse = Ref.lin(c(mat.color)), sun = Ref.lin(c(sun.color)) * 2.2,
                hemiSky = Ref.lin(c(hemi.color)) * 1.4, hemiGround = Ref.lin(c(hemi.groundColor)) * 1.4,
                roughness = rough.toDouble(), metalness = metal.toDouble(),
            )
            val expected = Ref.fromLinear(ch { it.r }, ch { it.g }, ch { it.b })
            assertColor(expected, px.center(), 2, "standard rough=$rough metal=$metal")
        }
    }

    @Test
    fun litSphereDarkensAwayFromTheSun() = GlFixture(96, 96).use { f ->
        val s = scene()
        s.add(DirectionalLight(0xffffff, 3f).apply { position.set(10f, 0f, 0f) })
        s.add(Mesh(TestGeo.sphere(1f), StandardMaterial().apply { color.setHex(0xcccccc) }).apply { position.set(0f, 0f, -3.2f) })
        val px = f.render(s, f.camera()).save("sphere_sun_x")
        val y = 48
        var prev = Double.MAX_VALUE
        // From the lit rim (right) to the terminator (centre): monotonically darker.
        for (x in 70 downTo 48 step 2) {
            val l = luma(px[x, y])
            assertTrue(l <= prev + 1.0, "luma must not increase towards the terminator at x=$x: $l > $prev")
            prev = l
        }
        assertTrue(luma(px[70, y]) > luma(px[48, y]) + 60, "lit side clearly brighter")
        assertTrue(luma(px[30, y]) < 3.0, "unlit side is black without hemisphere light")
    }

    @Test
    fun fogBlendsInOutputSpaceLikeThreeJs() = GlFixture().use { f ->
        val s = scene(0x87ceeb).apply { fog = Fog(0x87ceeb, 2f, 10f) }
        s.add(Mesh(TestGeo.quad(20f, 20f), BasicMaterial().apply { color.setHex(0xff0000) }).apply { position.set(0f, 0f, -5f) })
        val px = f.render(s, f.camera()).save("fog")
        val t = ((5.0 - 2) / (10 - 2)).let { it * it * (3 - 2 * it) }
        val exp = Ref.rgb(1.0 + (0x87 / 255.0 - 1) * t, (0xce / 255.0) * t, (0xeb / 255.0) * t)
        assertColor(exp, px.center(), 2, "fog")
    }

    @Test
    fun transparentSortsBackToFrontThenRenderOrder() = GlFixture().use { f ->
        val s = scene(0x000000)
        fun plane(hex: Int, z: Float) = Mesh(
            TestGeo.quad(4f, 4f),
            BasicMaterial().apply { color.setHex(hex); transparent = true; opacity = 0.5f },
        ).apply { position.set(0f, 0f, z) }
        val near = plane(0xff0000, -2f)
        val far = plane(0x0000ff, -4f)
        s.add(near, far) // added near first: sorting must still draw the far plane first
        val px = f.render(s, f.camera()).save("transparent_sorted")
        // dst = red*0.5 + (blue*0.5 + black*0.5)*0.5 (blending happens on sRGB bytes)
        assertColor(Ref.rgb(0.5, 0.0, 0.25), px.center(), 2, "back-to-front")
        near.renderOrder = -1 // renderOrder beats depth: near drawn first now
        val px2 = f.render(s, f.camera()).save("transparent_render_order")
        // Transparent materials still write depth (three.js default), so the far plane is rejected.
        assertColor(Ref.rgb(0.5, 0.0, 0.0), px2.center(), 2, "renderOrder + depthWrite")
        near.material.depthWrite = false
        val px3 = f.render(s, f.camera())
        assertColor(Ref.rgb(0.25, 0.0, 0.5), px3.center(), 2, "renderOrder without depthWrite")
    }

    @Test
    fun depthFlagsAndVisibility() = GlFixture().use { f ->
        val s = scene()
        val front = Mesh(TestGeo.quad(4f, 4f), BasicMaterial().apply { color.setHex(0x00ff00) }).apply { position.set(0f, 0f, -2f) }
        val overlay = Mesh(TestGeo.quad(1f, 1f), BasicMaterial().apply {
            color.setHex(0xff00ff); transparent = true; depthTest = false; depthWrite = false
        }).apply { position.set(0f, 0f, -5f); renderOrder = 10 }
        s.add(front, overlay)
        assertColor(0xff00ff, f.render(s, f.camera()).center(), 1, "depthTest=false draws on top")
        overlay.material.visible = false
        assertColor(0x00ff00, f.render(s, f.camera()).center(), 1, "material.visible=false hides")
        overlay.material.visible = true
        overlay.visible = false
        assertColor(0x00ff00, f.render(s, f.camera()).center(), 1, "node.visible=false hides")
    }

    @Test
    fun additiveBlending() = GlFixture().use { f ->
        val s = scene(0x404040)
        s.add(Mesh(TestGeo.quad(4f, 4f), BasicMaterial().apply {
            color.setHex(0x808080); transparent = true; opacity = 0.5f; blending = Blending.Additive
        }).apply { position.set(0f, 0f, -3f) })
        assertColor(0x404040 + 0x404040, f.render(s, f.camera()).center(), 1, "additive")
    }

    @Test
    fun instancingWithPerInstanceColours() = GlFixture(96, 32).use { f ->
        val s = scene()
        val inst = InstanceData(3, withColors = true)
        val m = Mat4()
        val q = Quat()
        for (i in 0 until 3) {
            inst.setMatrix(i, m.compose(Vec3((i - 1) * 1.5f, 0f, 0f), q, Vec3(1f, 1f, 1f)))
            // Instance colours are linear like three.js InstancedMesh.setColorAt.
            inst.colors!![i * 3 + i] = 1f
            for (c in 0 until 3) if (c != i) inst.colors[i * 3 + c] = 0f
        }
        inst.markDirty()
        val mesh = Mesh(TestGeo.quad(1f, 1f), BasicMaterial()).apply { position.set(0f, 0f, -3f); instances = inst }
        s.add(mesh)
        val cam = f.camera(40f)
        val px = f.render(s, cam).save("instancing")
        val y = 16
        assertColor(0xff0000, px[48 - 25, y], 1, "instance 0")
        assertColor(0x00ff00, px[48, y], 1, "instance 1")
        assertColor(0x0000ff, px[48 + 25, y], 1, "instance 2")
        inst.count = 1
        val px2 = f.render(s, cam)
        assertColor(0x000000, px2[48, y], 1, "count limits instances")
        assertEquals(1, f.renderer.stats.drawCalls)
    }

    @Test
    fun shadowsDarkenReceiver() = GlFixture(96, 96).use { f ->
        val s = scene(0x000000)
        val sun = DirectionalLight(0xffffff, 2f).apply {
            position.set(0f, 20f, 0.01f); castShadow = true
            shadow.left = -5f; shadow.right = 5f; shadow.top = 5f; shadow.bottom = -5f
            shadow.near = 1f; shadow.far = 40f; shadow.bias = -0.0005f; shadow.mapSize = 512
        }
        s.add(HemisphereLight(0xffffff, 0xffffff, 0.5f), sun)
        val ground = Mesh(TestGeo.quad(10f, 10f), StandardMaterial().apply { color.setHex(0xffffff) }).apply {
            rotation.x = (-PI / 2).toFloat(); receiveShadow = true
        }
        val box = Mesh(TestGeo.box(1.5f, 1.5f, 1.5f), StandardMaterial().apply { color.setHex(0xff0000) }).apply {
            position.set(0f, 2f, 0f); castShadow = true
        }
        s.add(ground, box)
        val cam = f.camera(50f).apply { position.set(0f, 8f, 6f); rotation.x = -0.93f }
        val px = f.render(s, cam).save("shadow_on")
        val shadowCalls = f.renderer.stats.shadowDrawCalls
        f.renderer.shadowsEnabled = false
        val off = f.render(s, cam).save("shadow_off")
        // Find the point under the box on screen.
        val p = Vec3(0f, 0f, 0f)
        cam.project(p)
        val sx = ((p.x + 1) / 2 * 96).toInt()
        val sy = ((1 - p.y) / 2 * 96).toInt()
        assertTrue(shadowCalls >= 1, "caster drawn into the shadow map")
        assertTrue(luma(px[sx, sy]) < luma(off[sx, sy]) - 40, "shadow darkens ${hex(px[sx, sy])} vs ${hex(off[sx, sy])}")
        assertColor(off[5, 90], px[5, 90], 2, "unshadowed ground unchanged")
        ground.receiveShadow = false
        f.renderer.shadowsEnabled = true
        assertColor(off[sx, sy], f.render(s, cam)[sx, sy], 2, "receiveShadow=false ignores shadow")
    }

    @Test
    fun shaderPatchHooksAndUniforms() = GlFixture().use { f ->
        val s = scene()
        val tint = Uniform.C(Color.hex(0x336699))
        val patch = ShaderPatch(
            key = "test-tint",
            fragmentHead = "uniform vec3 tint;",
            fragmentColor = "diffuseColor.rgb = tint;",
            uniforms = linkedMapOf("tint" to tint),
        )
        val mesh = Mesh(TestGeo.quad(4f, 4f), BasicMaterial().apply { this.patch = patch }).apply { position.set(0f, 0f, -3f) }
        s.add(mesh)
        assertColor(0x336699, f.render(s, f.camera()).save("patch_tint").center(), 1, "fragmentColor + Uniform.C")
        tint.value.setHex(0xcc2200)
        assertColor(0xcc2200, f.render(s, f.camera()).center(), 1, "uniform update")

        // Vertex hook + extra attribute (cactus-bristle style displacement) + output hook.
        val geo = TestGeo.quad(1f, 1f)
        geo.extraAttributes["spike"] = VertexAttribute(FloatArray(12) { if (it % 3 == 0) 1f else 0f }, 3)
        val amount = Uniform.F(0f)
        val spikePatch = ShaderPatch(
            key = "test-spike",
            vertexHead = "in vec3 spike;\nuniform float bristle;",
            vertexBody = "transformed += spike * bristle;",
            fragmentOutput = "outgoingLight = vec3( 1.0, 1.0, 0.0 );",
            uniforms = linkedMapOf("bristle" to amount),
        )
        s.clear()
        s.add(Mesh(geo, BasicMaterial().apply { this.patch = spikePatch }).apply { position.set(-1.5f, 0f, -3f) })
        f.renderer.frustumCulling = false
        val before = f.render(s, f.camera())
        amount.value = 1.5f
        val after = f.render(s, f.camera()).save("patch_spike")
        assertColor(0x000000, before.center(), 1, "not displaced yet")
        assertColor(0xffff00, after.center(), 1, "displaced into the centre, output hook applied")
    }

    @Test
    fun instancedMeshesCastShadows() = GlFixture(96, 96).use { f ->
        val s = scene(0x000000)
        val sun = DirectionalLight(0xffffff, 2f).apply {
            position.set(10f, 20f, 0f); castShadow = true
            shadow.left = -8f; shadow.right = 8f; shadow.top = 8f; shadow.bottom = -8f
            shadow.near = 1f; shadow.far = 60f; shadow.mapSize = 512
        }
        s.add(sun, Mesh(TestGeo.quad(10f, 10f), StandardMaterial()).apply { rotation.x = (-PI / 2).toFloat(); receiveShadow = true })
        val inst = InstanceData(2)
        inst.setMatrix(0, Mat4().compose(Vec3(-2f, 2f, 0f), Quat(), Vec3(1f, 1f, 1f)))
        inst.setMatrix(1, Mat4().compose(Vec3(2f, 2f, 0f), Quat(), Vec3(1f, 1f, 1f)))
        inst.markDirty()
        s.add(Mesh(TestGeo.box(1.5f, 0.2f, 1.5f), StandardMaterial()).apply { instances = inst; castShadow = true })
        val cam = f.camera(60f).apply { position.set(0f, 12f, 0f); rotation.x = (-PI / 2).toFloat() }
        val px = f.render(s, cam).save("shadow_instanced")
        fun at(x: Float): Int {
            val p = Vec3(x, 0f, 0f)
            cam.project(p)
            return px[((p.x + 1) / 2 * 96).toInt(), ((1 - p.y) / 2 * 96).toInt()]
        }
        // The sun comes from +X, so the right instance's shadow falls at x ~ [0.25, 1.75].
        assertTrue(luma(at(0.6f)) < luma(at(-0.6f)) - 40, "instanced caster shadows ${hex(at(0.6f))} vs ${hex(at(-0.6f))}")
        assertEquals(1, f.renderer.stats.shadowDrawCalls, "one instanced shadow draw")
    }

    @Test
    fun standardPatchEmissiveAndHazeHooks() = GlFixture().use { f ->
        val s = scene()
        val glow = Uniform.C(Color.hex(0x00ff00))
        val patch = ShaderPatch(
            key = "test-fx",
            vertexHead = "out vec3 vFxPos;",
            vertexBody = "vFxPos = position;",
            fragmentHead = "in vec3 vFxPos;\nuniform vec3 glow;",
            fragmentColor = "diffuseColor.rgb = vec3( 0.0 ); totalEmissive += glow * step( 0.0, vFxPos.x );",
            // Haze-like: blend to white above y = 0 using world position and camera position.
            fragmentOutput = "vec3 dir = normalize( vWorldPosition - cameraPosition ); outgoingLight = mix( outgoingLight, vec3( 1.0 ), step( 0.0, vWorldPosition.y ) * step( 0.0, -dir.z ) );",
            uniforms = linkedMapOf("glow" to glow),
        )
        s.add(Mesh(TestGeo.quad(4f, 4f), StandardMaterial().apply { this.patch = patch }).apply { position.set(0f, 0f, -3f) })
        val px = f.render(s, f.camera()).save("patch_standard")
        assertColor(0x000000, px[16, 48], 1, "left, below: no glow")
        assertColor(0x00ff00, px[48, 48], 1, "right, below: emissive glow")
        assertColor(0xffffff, px[16, 16], 1, "above: haze")
    }

    @Test
    fun customShaderSkyDome() = GlFixture(64, 64).use { f ->
        val s = scene(0xff00ff)
        val top = Uniform.C(Color.hex(0x3d8fe0))
        val horizon = Uniform.C(Color.hex(0xbfe6ff))
        val sky = CustomShaderMaterial(
            key = "sky",
            vertexSource = """
                out vec3 vDir;
                void main() {
                    vDir = normalize( position );
                    gl_Position = projectionMatrix * modelViewMatrix * vec4( position, 1.0 );
                }
            """.trimIndent(),
            fragmentSource = """
                uniform vec3 top;
                uniform vec3 horizon;
                in vec3 vDir;
                void main() {
                    float h = clamp( vDir.y * 1.8, 0.0, 1.0 );
                    fragColor = linearToOutputTexel( vec4( mix( horizon, top, pow( h, 0.7 ) ), 1.0 ) );
                }
            """.trimIndent(),
            uniforms = linkedMapOf("top" to top, "horizon" to horizon),
        ).apply { side = Side.Back; depthWrite = false; fog = false }
        s.add(Mesh(TestGeo.sphere(300f, 24, 12), sky).apply { renderOrder = -1 })
        val cam = f.camera(60f)
        val px = f.render(s, cam).save("sky_horizon")
        assertColor(0xbfe6ff, px[32, 40], 2, "below the horizon: horizon colour")
        cam.rotation.x = (PI / 2).toFloat()
        val up = f.render(s, cam).save("sky_zenith")
        assertColor(0x3d8fe0, up.center(), 3, "zenith: top colour")
    }

    @Test
    fun viewOffsetShiftsTheImage() = GlFixture(64, 64).use { f ->
        val s = scene()
        s.add(Mesh(TestGeo.quad(0.4f, 0.4f), BasicMaterial().apply { color.setHex(0xffffff) }).apply { position.set(0f, 0f, -3f) })
        val cam = f.camera()
        val base = f.render(s, cam)
        cam.setViewOffset(64f, 64f, 16f, 0f, 64f, 64f)
        cam.updateProjection()
        val shifted = f.render(s, cam).save("view_offset")
        assertColor(0xffffff, base[32, 32], 1, "centred without offset")
        assertColor(0x000000, shifted[32, 32], 1, "moved away")
        assertColor(0xffffff, shifted[16, 32], 1, "moved left by the x offset")
    }

    @Test
    fun normalMatrixHandlesNonUniformScale() = GlFixture().use { f ->
        // A 45-degree tilted face scaled 3x along X must shade like the same face with the scale baked in.
        fun tilted(sx: Float) = floatArrayOf(
            -0.5f * sx, -0.5f, 0.5f, 0.5f * sx, -0.5f, -0.5f, 0.5f * sx, 0.5f, -0.5f,
            -0.5f * sx, -0.5f, 0.5f, 0.5f * sx, 0.5f, -0.5f, -0.5f * sx, 0.5f, 0.5f,
        )
        fun render(geoScale: Float, nodeScale: Float): Int {
            val s = scene()
            s.add(DirectionalLight(0xffffff, 2f).apply { position.set(3f, 1f, 5f) })
            s.add(Mesh(TestGeo.flat(tilted(geoScale)), StandardMaterial().apply { roughness = 1f }).apply {
                position.set(0f, 0f, -3f); scale.set(nodeScale, 1f, 1f)
            })
            return f.render(s, f.camera()).center()
        }
        val baked = render(3f, 1f)
        val scaled = render(1f, 3f)
        assertColor(baked, scaled, 2, "non-uniform scale normal")
    }

    @Test
    fun doubleSidedBackFacesAreLitWithFlippedNormals() = GlFixture().use { f ->
        fun render(side: Side, rotY: Float): Int {
            val s = scene()
            s.add(DirectionalLight(0xffffff, 2f).apply { position.set(2f, 1f, 5f) })
            s.add(Mesh(TestGeo.quad(4f, 4f), StandardMaterial().apply { this.side = side; color.setHex(0xffffff) }).apply {
                position.set(0f, 0f, -3f); rotation.y = rotY
            })
            return f.render(s, f.camera()).center()
        }
        val front = render(Side.Front, 0f)
        val backOfDouble = render(Side.Double, PI.toFloat())
        assertColor(front, backOfDouble, 2, "double-sided back face")
        assertColor(0x000000, render(Side.Front, PI.toFloat()), 0, "front-side back face culled")
        // BackSide: visible from behind, normal flipped (FLIP_SIDED) so it is lit like a front face.
        assertColor(front, render(Side.Back, PI.toFloat()), 2, "back side")
    }

    @Test
    fun flatShadingUsesFaceNormals() = GlFixture(96, 96).use { f ->
        fun distinct(flat: Boolean): Int {
            val s = scene()
            s.add(DirectionalLight(0xffffff, 3f).apply { position.set(10f, 4f, 3f) })
            s.add(Mesh(TestGeo.sphere(1f, 8, 6), StandardMaterial().apply { flatShading = flat; color.setHex(0xdddddd) }).apply {
                position.set(0f, 0f, -3.2f)
            })
            return f.render(s, f.camera()).save(if (flat) "flat_sphere" else "smooth_sphere").argb.toSet().size
        }
        val flat = distinct(true)
        val smooth = distinct(false)
        assertTrue(flat < 80, "faceted sphere has one shade per face (got $flat)")
        assertTrue(smooth > 3 * flat, "smooth sphere has gradients ($smooth vs $flat)")
    }

    @Test
    fun textureIsFlippedAndSrgbDecoded() = GlFixture().use { f ->
        val tex = Texture(2, 2).apply {
            minFilter = de.robinrehbein.birdy.engine.scene.Filter.Nearest
            magFilter = de.robinrehbein.birdy.engine.scene.Filter.Nearest
            // top row red, bottom row 50% grey (sRGB bytes)
            for (i in 0 until 2) { pixels[i * 4] = 0xff.toByte(); pixels[i * 4 + 3] = 0xff.toByte() }
            for (i in 2 until 4) { for (c in 0 until 3) pixels[i * 4 + c] = 0x80.toByte(); pixels[i * 4 + 3] = 0xff.toByte() }
        }
        val s = scene()
        s.add(Mesh(TestGeo.quad(2f, 2f), BasicMaterial().apply { map = tex }).apply { position.set(0f, 0f, -2f) })
        val px = f.render(s, f.camera()).save("texture")
        assertColor(0xff0000, px[32, 20], 1, "top row at the top (flipY)")
        assertColor(0x808080, px[32, 44], 1, "sRGB texel round-trips")
    }

    @Test
    fun msaaTargetSmoothsEdges() {
        fun edgeValues(samples: Int): Set<Int> = GlFixture(64, 64, samples).use { f ->
            val s = scene()
            s.add(Mesh(TestGeo.quad(1f, 1f), BasicMaterial().apply { color.setHex(0xffffff) }).apply {
                position.set(0f, 0f, -3f); rotation.z = 0.3f
            })
            val px = f.render(s, f.camera()).save("msaa_$samples")
            px.argb.map { it and 0xFF }.toSet()
        }
        assertEquals(setOf(0, 255), edgeValues(0))
        assertTrue(edgeValues(4).size > 2, "4x MSAA produces intermediate edge values")
    }

    @Test
    fun resolutionScaleRendersLowResAndUpscales() = GlFixture(64, 64).use { f ->
        val s = scene(0x0000ff)
        s.add(Mesh(TestGeo.quad(1f, 1f), BasicMaterial().apply { color.setHex(0xffffff) }).apply { position.set(0f, 0f, -3f) })
        val out = f.target as GlRenderTarget
        f.renderer.defaultFramebuffer = out.readFbo
        f.renderer.setSurfaceSize(64, 64)
        f.renderer.resolutionScale = 0.5f
        f.renderer.msaaSamples = 0
        f.renderer.render(s, f.camera(), null)
        val px = Pixels(f.renderer.readPixels(f.target), 64, 64).save("scale_half")
        assertColor(0x0000ff, px[2, 2], 1, "background")
        assertColor(0xffffff, px[32, 32], 1, "object")
        assertEquals(GL.NO_ERROR, f.gl.getError())
    }

    @Test
    fun contextLossAndReuploadOnVersionChange() = GlFixture().use { f ->
        val s = scene()
        val geo = TestGeo.quad(4f, 4f).apply { colors = FloatArray(12) { if (it % 3 == 0) 1f else 0f } }
        s.add(Mesh(geo, BasicMaterial().apply { vertexColors = true }).apply { position.set(0f, 0f, -3f) })
        assertColor(0xff0000, f.render(s, f.camera()).center(), 1, "vertex colour")
        geo.colors!!.fill(0f)
        for (i in 0 until 4) geo.colors!![i * 3 + 1] = 1f
        geo.markDirty()
        assertColor(0x00ff00, f.render(s, f.camera()).center(), 1, "re-upload after markDirty")
        f.renderer.onContextCreated()
        assertColor(0x00ff00, f.render(s, f.camera()).center(), 1, "after context re-creation")
    }

    @Test
    fun programCacheIsKeyedByVariant() = GlFixture().use { f ->
        val s = scene()
        val a = BasicMaterial().apply { color.setHex(0xff0000) }
        val b = BasicMaterial().apply { color.setHex(0x00ff00) }
        s.add(Mesh(TestGeo.quad(), a).apply { position.set(-1f, 0f, -4f) }, Mesh(TestGeo.quad(), b).apply { position.set(1f, 0f, -4f) })
        f.render(s, f.camera())
        assertEquals(1, f.renderer.stats.programs, "same variant shares a program")
        b.vertexColors = true
        f.render(s, f.camera())
        assertEquals(2, f.renderer.stats.programs, "vertexColors is a separate variant")
        assertEquals(2, f.renderer.stats.drawCalls)
        assertEquals(4, f.renderer.stats.triangles)
    }

    @Test
    fun frustumCullingSkipsOffscreenMeshes() = GlFixture().use { f ->
        val s = scene()
        s.add(Mesh(TestGeo.quad(), BasicMaterial()).apply { position.set(0f, 0f, -4f) })
        s.add(Mesh(TestGeo.quad(), BasicMaterial()).apply { position.set(0f, 0f, 10f) })
        f.render(s, f.camera())
        assertEquals(1, f.renderer.stats.drawCalls)
        assertEquals(1, f.renderer.stats.culled)
    }

    @Test
    fun emissiveAddsAfterLighting() = GlFixture().use { f ->
        val s = scene()
        s.add(Mesh(TestGeo.quad(4f, 4f), StandardMaterial().apply {
            color.setHex(0x000000); emissive.setHex(0xb07800); emissiveIntensity = 1f
        }).apply { position.set(0f, 0f, -3f) })
        assertColor(0xb07800, f.render(s, f.camera()).center(), 1, "emissive only")
    }

    @Test
    fun hemisphereLightFollowsNormal() = GlFixture().use { f ->
        fun shade(rotX: Float): Int {
            val s = scene()
            s.add(HemisphereLight(0xffffff, 0x000000, 1f))
            s.add(Mesh(TestGeo.quad(8f, 8f), StandardMaterial().apply { roughness = 1f }).apply {
                position.set(0f, 0f, -3f); rotation.x = rotX
            })
            return f.render(s, f.camera()).center()
        }
        val facing = shade(0f)
        val up = shade(-1.2f)
        assertTrue(luma(up) > luma(facing) + 20, "upward-tilted face gets more sky: ${hex(up)} vs ${hex(facing)}")
    }
}
