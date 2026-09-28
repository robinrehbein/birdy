package de.robinrehbein.birdy.engine.gl

import de.robinrehbein.birdy.engine.RenderBackend
import de.robinrehbein.birdy.engine.RenderStats
import de.robinrehbein.birdy.engine.RenderTarget
import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.scene.BasicMaterial
import de.robinrehbein.birdy.engine.scene.Blending
import de.robinrehbein.birdy.engine.scene.CustomShaderMaterial
import de.robinrehbein.birdy.engine.scene.DirectionalLight
import de.robinrehbein.birdy.engine.scene.Geometry
import de.robinrehbein.birdy.engine.scene.HemisphereLight
import de.robinrehbein.birdy.engine.scene.InstanceData
import de.robinrehbein.birdy.engine.scene.Material
import de.robinrehbein.birdy.engine.scene.Mesh
import de.robinrehbein.birdy.engine.scene.Node
import de.robinrehbein.birdy.engine.scene.PerspectiveCamera
import de.robinrehbein.birdy.engine.scene.Scene
import de.robinrehbein.birdy.engine.scene.Side
import de.robinrehbein.birdy.engine.scene.StandardMaterial
import de.robinrehbein.birdy.engine.scene.Texture
import de.robinrehbein.birdy.engine.scene.Uniform
import kotlin.math.roundToInt

/**
 * OpenGL ES 3.0 implementation of [RenderBackend], shared verbatim by Android and desktop.
 *
 * Mirrors what three.js r186 `WebGLRenderer` does for Birdy's scenes: opaque pass sorted by
 * renderOrder then front-to-back, transparent pass sorted by renderOrder then back-to-front
 * (double-sided transparent materials drawn back faces first), `MeshStandardMaterial` lighting
 * for one hemisphere + one directional light with a PCF shadow map, linear fog, sRGB output,
 * instancing (matrices + colours), shader patches, custom shader materials, frustum culling,
 * resolution scaling with optional MSAA, and lazy GPU re-creation after context loss.
 */
class GlRenderer(internal val gl: Gl) : RenderBackend {
    override var resolutionScale = 1f
    override var shadowsEnabled = true
    override val stats = RenderStats()

    /**
     * MSAA samples of the offscreen buffer used when [resolutionScale] != 1 (JS: antialias
     * true). At scale 1 the scene is drawn straight into the default framebuffer, whose MSAA is
     * chosen by the EGL config.
     */
    var msaaSamples = 4

    /**
     * Framebuffer that `render(scene, camera, null)` targets (0 = the window surface). Tests and
     * embedders may point it at their own FBO.
     */
    var defaultFramebuffer = 0

    /** Frustum culling of non-instanced meshes (three.js `frustumCulled = true` default). */
    var frustumCulling = true

    /** Incremented per context; GL names from older generations are invalid. */
    internal var generation = 0
        private set
    internal var maxSamples = 0
        private set
    private var maxAnisotropy = 1f

    private var surfaceWidth = 1
    private var surfaceHeight = 1

    private val state = GlState(gl)
    private val programs = HashMap<String, GlProgram>()
    private val materialPrograms = HashMap<Material, MaterialProgram>()
    private val geometries = HashMap<Geometry, GpuGeometry>()
    private val instances = HashMap<InstanceData, GpuInstances>()
    private val textures = HashMap<Texture, GpuTexture>()
    private val bounds = HashMap<Geometry, GeometryBounds>()
    private val targets = HashSet<GlRenderTarget>()
    private val shadowMap = ShadowMap(gl)
    private var depthProgram: GlProgram? = null
    private var depthInstancedProgram: GlProgram? = null
    private var presentProgram: GlProgram? = null
    private var emptyVao = 0
    private var dfgTexture = 0
    private var sceneBuffer: GlRenderTarget? = null

    private var frame = 0
    private var passStamp = 0

    // Per-frame scratch.
    private val opaque = ArrayList<RenderItem>()
    private val transparent = ArrayList<RenderItem>()
    private val itemPool = ArrayList<RenderItem>()
    private var itemCount = 0
    private val viewProjection = FloatArray(16)
    private val frustum = Frustum()
    private val modelView = FloatArray(16)
    private val normalMatrix = FloatArray(9)
    private val v3 = FloatArray(3)
    private val cameraPos = FloatArray(3)
    private val sunDir = FloatArray(3)
    private val hemiDir = FloatArray(3)
    private var hemi: HemisphereLight? = null
    private var sun: DirectionalLight? = null
    private var shadowActive = false
    private var frontFaceCw = false

    private val opaqueOrder = Comparator<RenderItem> { a, b ->
        when {
            a.renderOrder != b.renderOrder -> a.renderOrder.compareTo(b.renderOrder)
            a.z != b.z -> a.z.compareTo(b.z)
            else -> a.index.compareTo(b.index)
        }
    }
    private val transparentOrder = Comparator<RenderItem> { a, b ->
        when {
            a.renderOrder != b.renderOrder -> a.renderOrder.compareTo(b.renderOrder)
            a.z != b.z -> b.z.compareTo(a.z)
            else -> a.index.compareTo(b.index)
        }
    }

    override fun onContextCreated() {
        // A new context invalidates every GL name; drop caches so objects are recreated lazily.
        programs.clear()
        materialPrograms.clear()
        geometries.clear()
        instances.clear()
        textures.clear()
        shadowMap.forget()
        depthProgram = null
        depthInstancedProgram = null
        presentProgram = null
        emptyVao = 0
        dfgTexture = 0
        sceneBuffer = null
        generation++
        initContext()
    }

    private fun initContext() {
        state.invalidate()
        maxSamples = gl.getInteger(GL.MAX_SAMPLES).coerceAtLeast(0)
        val ext = gl.getString(GL.EXTENSIONS).orEmpty()
        maxAnisotropy = if (ext.contains("GL_EXT_texture_filter_anisotropic")) {
            gl.getInteger(GL.MAX_TEXTURE_MAX_ANISOTROPY_EXT).toFloat().coerceAtLeast(1f)
        } else 1f
        // Generic attribute values for arrays a geometry doesn't provide (context state).
        gl.vertexAttrib3f(Attr.NORMAL, 0f, 1f, 0f)
        gl.vertexAttrib3f(Attr.COLOR, 1f, 1f, 1f)
        gl.vertexAttrib3f(Attr.UV, 0f, 0f, 0f)
        gl.frontFace(GL.CCW)
        gl.depthFunc(GL.LEQUAL)
        frontFaceCw = false

        dfgTexture = gl.genTexture()
        gl.bindTexture(GL.TEXTURE_2D, dfgTexture)
        gl.texImage2D(GL.TEXTURE_2D, 0, GL.RG16F, DfgLut.SIZE, DfgLut.SIZE, GL.RG, GL.HALF_FLOAT, DfgLut.bytes())
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.LINEAR)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.LINEAR)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)
        gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)
        gl.bindTexture(GL.TEXTURE_2D, 0)
        emptyVao = gl.genVertexArray()
    }

    override fun setSurfaceSize(width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
    }

    override fun render(scene: Scene, camera: PerspectiveCamera, target: RenderTarget?) {
        if (generation == 0) onContextCreated()
        frame++
        stats.drawCalls = 0
        stats.triangles = 0
        stats.shadowDrawCalls = 0
        stats.culled = 0
        state.invalidate()

        scene.updateWorldMatrix(null)
        if (camera.parent == null) camera.updateWorldMatrix(null)
        camera.updateView()
        collectLights(scene)

        val sunLight = sun
        shadowActive = shadowsEnabled && sunLight != null && sunLight.castShadow
        if (shadowActive) renderShadowMap(scene, sunLight!!)

        // Output framebuffer.
        val t = target as GlRenderTarget?
        val out: GlRenderTarget?
        if (t != null) {
            t.ensure(gl)
            out = t
        } else {
            val scale = resolutionScale.coerceIn(0.1f, 2f)
            out = if (scale == 1f) null else sceneBufferFor(
                (surfaceWidth * scale).roundToInt().coerceAtLeast(1),
                (surfaceHeight * scale).roundToInt().coerceAtLeast(1),
            )
        }
        gl.bindFramebuffer(GL.FRAMEBUFFER, out?.drawFbo ?: defaultFramebuffer)
        gl.viewport(0, 0, out?.width ?: surfaceWidth, out?.height ?: surfaceHeight)

        val bg = scene.background
        // three.js converts the background to the output colour space (sRGB), i.e. the hex value.
        if (bg != null) gl.clearColor(bg.r, bg.g, bg.b, 1f) else gl.clearColor(0f, 0f, 0f, 0f)
        state.depthWrite(true)
        state.colorWrite(true)
        gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT or GL.STENCIL_BUFFER_BIT)

        GlMath.mul(viewProjection, camera.projectionMatrix.e, camera.viewMatrix.e)
        frustum.set(viewProjection)
        val cw = camera.worldMatrix.e
        cameraPos[0] = cw[12]; cameraPos[1] = cw[13]; cameraPos[2] = cw[14]
        prepareLightUniforms(camera)

        itemCount = 0
        opaque.clear()
        transparent.clear()
        collect(scene)
        opaque.sortWith(opaqueOrder)
        transparent.sortWith(transparentOrder)

        passStamp++
        bindPassTextures()
        for (item in opaque) drawItem(item.mesh, scene, camera, item.mesh.material.side)
        for (item in transparent) {
            val mat = item.mesh.material
            if (mat.side == Side.Double) {
                drawItem(item.mesh, scene, camera, Side.Back)
                drawItem(item.mesh, scene, camera, Side.Front)
            } else {
                drawItem(item.mesh, scene, camera, mat.side)
            }
        }
        gl.bindVertexArray(0)
        state.depthWrite(true)
        state.blending(GlState.NO_BLEND)
        setFrontFace(false)

        if (t != null) t.dirty = true
        else if (out != null) present(out)
        stats.programs = programs.size
        if (frame % SWEEP_INTERVAL == 0) sweep()
    }

    // ---------------------------------------------------------------- scene traversal

    private fun collectLights(scene: Scene) {
        hemi = null
        sun = null
        findLights(scene)
    }

    private fun findLights(n: Node) {
        if (!n.visible) return
        if (n is HemisphereLight && hemi == null) hemi = n
        if (n is DirectionalLight && sun == null) sun = n
        for (c in n.children) findLights(c)
    }

    private fun collect(node: Node) {
        if (!node.visible) return
        if (node is Mesh && node.material.visible) addItem(node)
        for (c in node.children) collect(c)
    }

    private fun addItem(mesh: Mesh) {
        val w = mesh.worldMatrix.e
        val inst = mesh.instances
        if (inst != null) {
            if (inst.count <= 0) return
            GlMath.project(v3, viewProjection, w[12], w[13], w[14])
        } else {
            val b = boundsFor(mesh.geometry)
            GlMath.transformPoint(v3, w, b.cx, b.cy, b.cz)
            if (frustumCulling && !frustum.intersectsSphere(v3[0], v3[1], v3[2], b.radius * GlMath.maxScaleOnAxis(w))) {
                stats.culled++
                return
            }
            GlMath.project(v3, viewProjection, v3[0], v3[1], v3[2])
        }
        if (itemCount == itemPool.size) itemPool.add(RenderItem())
        val item = itemPool[itemCount]
        item.mesh = mesh
        item.z = v3[2]
        item.renderOrder = mesh.renderOrder
        item.index = itemCount++
        if (mesh.material.transparent) transparent.add(item) else opaque.add(item)
    }

    private fun boundsFor(geo: Geometry): GeometryBounds {
        val b = bounds.getOrPut(geo) { GeometryBounds() }
        b.sync(geo)
        b.lastUsed = frame
        return b
    }

    // ---------------------------------------------------------------- uniforms

    private fun prepareLightUniforms(camera: PerspectiveCamera) {
        val view = camera.viewMatrix.e
        val s = sun
        if (s != null) {
            val w = s.worldMatrix.e
            GlMath.transformDirection(sunDir, view, w[12] - s.target.x, w[13] - s.target.y, w[14] - s.target.z)
        }
        val h = hemi
        if (h != null) {
            val w = h.worldMatrix.e
            // three.js hemisphere lights sit at DEFAULT_UP (0, 1, 0); the node default is the origin.
            val zero = w[12] == 0f && w[13] == 0f && w[14] == 0f
            if (zero) GlMath.transformDirection(hemiDir, view, 0f, 1f, 0f)
            else GlMath.transformDirection(hemiDir, view, w[12], w[13], w[14])
        }
    }

    private fun bindPassTextures() {
        gl.activeTexture(GL.TEXTURE1)
        gl.bindTexture(GL.TEXTURE_2D, dfgTexture)
        gl.activeTexture(GL.TEXTURE2)
        gl.bindTexture(GL.TEXTURE_2D, if (shadowActive) shadowMap.texture else 0)
        gl.activeTexture(GL.TEXTURE0)
    }

    private fun passUniforms(p: GlProgram, scene: Scene, camera: PerspectiveCamera) {
        if (p.passStamp == passStamp) return
        p.passStamp = passStamp
        gl.uniformMatrix4fv(p.loc("viewMatrix"), 1, camera.viewMatrix.e)
        gl.uniformMatrix4fv(p.loc("projectionMatrix"), 1, camera.projectionMatrix.e)
        gl.uniform3f(p.loc("cameraPosition"), cameraPos[0], cameraPos[1], cameraPos[2])
        val h = hemi
        if (h != null) {
            setLinearColor(p.loc("hemiSkyColor"), h.color, h.intensity)
            setLinearColor(p.loc("hemiGroundColor"), h.groundColor, h.intensity)
            gl.uniform3f(p.loc("hemiDirection"), hemiDir[0], hemiDir[1], hemiDir[2])
        } else {
            gl.uniform3f(p.loc("hemiSkyColor"), 0f, 0f, 0f)
            gl.uniform3f(p.loc("hemiGroundColor"), 0f, 0f, 0f)
            gl.uniform3f(p.loc("hemiDirection"), 0f, 1f, 0f)
        }
        val s = sun
        if (s != null) {
            setLinearColor(p.loc("sunColor"), s.color, s.intensity)
            gl.uniform3f(p.loc("sunDirection"), sunDir[0], sunDir[1], sunDir[2])
        } else {
            gl.uniform3f(p.loc("sunColor"), 0f, 0f, 0f)
            gl.uniform3f(p.loc("sunDirection"), 0f, 1f, 0f)
        }
        val fog = scene.fog
        if (fog != null) {
            // Rendering to the canvas, three.js uploads the fog colour in output (sRGB) space.
            gl.uniform3f(p.loc("fogColor"), fog.color.r, fog.color.g, fog.color.b)
            gl.uniform1f(p.loc("fogNear"), fog.near)
            gl.uniform1f(p.loc("fogFar"), fog.far)
        }
        gl.uniform1i(p.loc("dfgLUT"), 1)
        if (shadowActive) {
            val sh = sun!!.shadow
            gl.uniform1i(p.loc("shadowMap"), 2)
            gl.uniformMatrix4fv(p.loc("shadowMatrix"), 1, shadowMap.matrix)
            gl.uniform2f(p.loc("shadowMapSize"), shadowMap.size.toFloat(), shadowMap.size.toFloat())
            gl.uniform1f(p.loc("shadowBias"), sh.bias)
            gl.uniform1f(p.loc("shadowNormalBias"), sh.normalBias)
            gl.uniform1f(p.loc("shadowRadius"), SHADOW_RADIUS)
            gl.uniform1f(p.loc("shadowIntensity"), SHADOW_INTENSITY)
        }
    }

    private fun setLinearColor(loc: Int, c: Color, intensity: Float) {
        if (loc < 0) return
        gl.uniform3f(
            loc,
            Color.srgbToLinear(c.r) * intensity,
            Color.srgbToLinear(c.g) * intensity,
            Color.srgbToLinear(c.b) * intensity,
        )
    }

    private fun applyUniform(loc: Int, u: Uniform, unit: Int): Int {
        if (loc < 0) return unit
        when (u) {
            is Uniform.F -> gl.uniform1f(loc, u.value)
            is Uniform.V3 -> gl.uniform3f(loc, u.value.x, u.value.y, u.value.z)
            is Uniform.C -> setLinearColor(loc, u.value, 1f)
            is Uniform.M4 -> gl.uniformMatrix4fv(loc, 1, u.value.e)
            is Uniform.Tex -> {
                val tex = u.value ?: return unit
                gl.activeTexture(GL.TEXTURE0 + unit)
                gl.bindTexture(GL.TEXTURE_2D, textureFor(tex))
                gl.uniform1i(loc, unit)
                gl.activeTexture(GL.TEXTURE0)
                return unit + 1
            }
        }
        return unit
    }

    // ---------------------------------------------------------------- drawing

    private fun drawItem(mesh: Mesh, scene: Scene, camera: PerspectiveCamera, side: Side) {
        val mat = mesh.material
        val geo = mesh.geometry
        val inst = mesh.instances
        val gpu = geometryFor(geo)
        val count = drawCount(geo, gpu)
        if (count <= 0) return
        val gpuInst = inst?.let { instancesFor(it) }
        val instanceCount = inst?.count?.coerceAtMost(inst.capacity) ?: 1
        val program = programFor(mat, gpu, inst, side, scene)
        state.useProgram(program.id)
        passUniforms(program, scene, camera)

        state.cull(cullFace(side))
        state.depthTest(mat.depthTest)
        state.depthWrite(mat.depthWrite)
        state.blending(
            when {
                !mat.transparent && mat.blending == Blending.Normal -> GlState.NO_BLEND
                mat.blending == Blending.Additive -> GlState.ADDITIVE
                else -> GlState.NORMAL
            },
        )
        val w = mesh.worldMatrix.e
        setFrontFace(det3(w) < 0f)

        GlMath.mul(modelView, camera.viewMatrix.e, w)
        GlMath.normalMatrix(normalMatrix, modelView)
        gl.uniformMatrix4fv(program.loc("modelMatrix"), 1, w)
        gl.uniformMatrix4fv(program.loc("modelViewMatrix"), 1, modelView)
        gl.uniformMatrix3fv(program.loc("normalMatrix"), 1, normalMatrix)
        setLinearColor(program.loc("diffuse"), mat.color, 1f)
        gl.uniform1f(program.loc("opacity"), mat.opacity)
        if (mat is StandardMaterial) {
            setLinearColor(program.loc("emissive"), mat.emissive, mat.emissiveIntensity)
            gl.uniform1f(program.loc("roughness"), mat.roughness)
            gl.uniform1f(program.loc("metalness"), mat.metalness)
            if (shadowActive) gl.uniform1i(program.loc("receiveShadow"), if (mesh.receiveShadow) 1 else 0)
        } else {
            val loc = program.loc("emissive")
            if (loc >= 0) gl.uniform3f(loc, 0f, 0f, 0f)
        }
        val map = mat.map
        if (map != null && mat !is CustomShaderMaterial) {
            gl.bindTexture(GL.TEXTURE_2D, textureFor(map))
            gl.uniform1i(program.loc("map"), 0)
            gl.uniform4f(program.loc("uvTransform"), map.repeatU, map.repeatV, map.offsetU, map.offsetV)
        }
        var unit = FIRST_USER_TEXTURE_UNIT
        val uniforms = if (mat is CustomShaderMaterial) mat.uniforms else mat.patch?.uniforms
        uniforms?.forEach { (name, u) -> unit = applyUniform(program.loc(name), u, unit) }

        gl.bindVertexArray(gpu.vao)
        if (gpuInst != null) gpu.attachInstances(gpuInst) else gpu.detachInstances()
        submit(gpu, count, if (inst != null) instanceCount else 0)
        stats.drawCalls++
        stats.triangles += count / 3 * instanceCount
    }

    private fun submit(gpu: GpuGeometry, count: Int, instanceCount: Int) {
        if (instanceCount > 0) {
            if (gpu.indexed) gl.drawElementsInstanced(GL.TRIANGLES, count, GL.UNSIGNED_INT, 0, instanceCount)
            else gl.drawArraysInstanced(GL.TRIANGLES, 0, count, instanceCount)
        } else {
            if (gpu.indexed) gl.drawElements(GL.TRIANGLES, count, GL.UNSIGNED_INT, 0)
            else gl.drawArrays(GL.TRIANGLES, 0, count)
        }
    }

    private fun drawCount(geo: Geometry, gpu: GpuGeometry): Int =
        if (geo.drawCount >= 0) minOf(geo.drawCount, gpu.elementCount) else gpu.elementCount

    private fun cullFace(side: Side) = when (side) {
        Side.Front -> GL.BACK
        Side.Back -> GL.FRONT
        Side.Double -> 0
    }

    private fun setFrontFace(cw: Boolean) {
        if (cw == frontFaceCw) return
        frontFaceCw = cw
        gl.frontFace(if (cw) GL.CW else GL.CCW)
    }

    private fun det3(m: FloatArray): Float =
        m[0] * (m[5] * m[10] - m[6] * m[9]) - m[4] * (m[1] * m[10] - m[2] * m[9]) + m[8] * (m[1] * m[6] - m[2] * m[5])

    // ---------------------------------------------------------------- shadow pass

    private fun renderShadowMap(scene: Scene, light: DirectionalLight) {
        shadowMap.ensure(light.shadow.mapSize.coerceIn(1, MAX_SHADOW_MAP))
        shadowMap.updateMatrices(light)
        gl.bindFramebuffer(GL.FRAMEBUFFER, shadowMap.fbo)
        gl.viewport(0, 0, shadowMap.size, shadowMap.size)
        state.depthWrite(true)
        state.depthTest(true)
        state.colorWrite(false)
        state.blending(GlState.NO_BLEND)
        gl.clear(GL.DEPTH_BUFFER_BIT)
        drawShadowCasters(scene)
        gl.bindVertexArray(0)
        state.colorWrite(true)
        setFrontFace(false)
    }

    private fun drawShadowCasters(node: Node) {
        if (!node.visible) return
        if (node is Mesh && node.castShadow && node.material.visible) drawShadowCaster(node)
        for (c in node.children) drawShadowCasters(c)
    }

    private fun drawShadowCaster(mesh: Mesh) {
        val inst = mesh.instances
        if (inst != null && inst.count <= 0) return
        val w = mesh.worldMatrix.e
        if (inst == null && frustumCulling) {
            val b = boundsFor(mesh.geometry)
            GlMath.transformPoint(v3, w, b.cx, b.cy, b.cz)
            if (!shadowMap.frustum.intersectsSphere(v3[0], v3[1], v3[2], b.radius * GlMath.maxScaleOnAxis(w))) return
        }
        val gpu = geometryFor(mesh.geometry)
        val count = drawCount(mesh.geometry, gpu)
        if (count <= 0) return
        val program = if (inst != null) {
            depthInstancedProgram ?: GlProgram(gl, ShaderLib.depthVertex(true), ShaderLib.depthFragment(), emptyList()).also { depthInstancedProgram = it }
        } else {
            depthProgram ?: GlProgram(gl, ShaderLib.depthVertex(false), ShaderLib.depthFragment(), emptyList()).also { depthProgram = it }
        }
        state.useProgram(program.id)
        if (program.passStamp != frame) {
            program.passStamp = frame
            gl.uniformMatrix4fv(program.loc("lightViewProjection"), 1, shadowMap.viewProjection)
        }
        // three.js renders the opposite side into the shadow map (FrontSide -> BackSide).
        state.cull(
            when (mesh.material.side) {
                Side.Front -> GL.FRONT
                Side.Back -> GL.BACK
                Side.Double -> 0
            },
        )
        setFrontFace(det3(w) < 0f)
        gl.uniformMatrix4fv(program.loc("modelMatrix"), 1, w)
        gl.bindVertexArray(gpu.vao)
        val instanceCount = inst?.count?.coerceAtMost(inst.capacity) ?: 1
        if (inst != null) gpu.attachInstances(instancesFor(inst)) else gpu.detachInstances()
        submit(gpu, count, if (inst != null) instanceCount else 0)
        stats.drawCalls++
        stats.shadowDrawCalls++
        stats.triangles += count / 3 * instanceCount
    }

    // ---------------------------------------------------------------- output

    private fun sceneBufferFor(width: Int, height: Int): GlRenderTarget {
        val cur = sceneBuffer
        if (cur != null && cur.width == width && cur.height == height && cur.samples == minOf(msaaSamples, maxSamples)) {
            cur.ensure(gl)
            return cur
        }
        cur?.dispose()
        val buf = GlRenderTarget(this, width, height, msaaSamples)
        buf.ensure(gl)
        sceneBuffer = buf
        return buf
    }

    /** Upscales [buffer] to the default framebuffer with a bilinear full-screen triangle. */
    private fun present(buffer: GlRenderTarget) {
        buffer.dirty = true
        buffer.resolve(gl)
        gl.bindFramebuffer(GL.FRAMEBUFFER, defaultFramebuffer)
        gl.viewport(0, 0, surfaceWidth, surfaceHeight)
        val p = presentProgram ?: GlProgram(gl, PRESENT_VS, PRESENT_FS, emptyList()).also { presentProgram = it }
        state.useProgram(p.id)
        state.depthTest(false)
        state.depthWrite(false)
        state.cull(0)
        state.blending(GlState.NO_BLEND)
        gl.activeTexture(GL.TEXTURE0)
        gl.bindTexture(GL.TEXTURE_2D, buffer.colorTexture)
        gl.uniform1i(p.loc("src"), 0)
        gl.bindVertexArray(emptyVao)
        gl.drawArrays(GL.TRIANGLES, 0, 3)
        gl.bindVertexArray(0)
        state.depthWrite(true)
    }

    // ---------------------------------------------------------------- caches

    private fun programFor(mat: Material, gpu: GpuGeometry, inst: InstanceData?, side: Side, scene: Scene): GlProgram {
        var flags = 0
        if (inst != null) {
            flags = flags or F_INSTANCED
            if (inst.colors != null) flags = flags or F_INSTANCED_COLOR
        }
        if (mat.vertexColors) flags = flags or F_VERTEX_COLOR
        if (mat.map != null && mat !is CustomShaderMaterial) flags = flags or F_MAP
        if (mat.fog && scene.fog != null) flags = flags or F_FOG
        if (mat is StandardMaterial) {
            flags = flags or F_LIT
            if (mat.flatShading) flags = flags or F_FLAT
            if (shadowActive) flags = flags or F_SHADOWMAP
        }
        when (side) {
            Side.Double -> flags = flags or F_DOUBLE
            Side.Back -> flags = flags or F_FLIP
            Side.Front -> {}
        }
        if (!mat.transparent && mat.blending == Blending.Normal) flags = flags or F_OPAQUE

        val patch = mat.patch
        val cached = materialPrograms[mat]
        if (cached != null && cached.flags == flags && cached.patch === patch && cached.extrasKey == gpu.extrasKey &&
            cached.generation == generation
        ) {
            cached.lastUsed = frame
            return cached.program
        }
        val kind = when (mat) {
            is CustomShaderMaterial -> "custom:${mat.key}"
            is BasicMaterial -> "basic"
            is StandardMaterial -> "standard"
        }
        val key = "$kind|${if (mat is CustomShaderMaterial) "" else patch?.key.orEmpty()}|$flags|${gpu.extrasKey}"
        val program = programs.getOrPut(key) {
            val defines = defineNames(flags)
            when (mat) {
                is CustomShaderMaterial -> GlProgram(
                    gl,
                    ShaderLib.customVertex(mat.vertexSource, defines),
                    ShaderLib.customFragment(mat.fragmentSource, defines),
                    gpu.extraNames,
                )
                else -> GlProgram(gl, ShaderLib.vertex(defines, patch), ShaderLib.fragment(defines, patch), gpu.extraNames)
            }
        }
        materialPrograms[mat] = MaterialProgram(flags, patch, gpu.extrasKey, program, generation).also { it.lastUsed = frame }
        return program
    }

    private fun geometryFor(geo: Geometry): GpuGeometry {
        val gpu = geometries.getOrPut(geo) { GpuGeometry(gl, geo) }
        gpu.syncIfDirty()
        gpu.lastUsed = frame
        return gpu
    }

    private fun instancesFor(data: InstanceData): GpuInstances {
        val gpu = instances.getOrPut(data) { GpuInstances(gl, data) }
        gpu.syncIfDirty()
        gpu.lastUsed = frame
        return gpu
    }

    private fun textureFor(tex: Texture): Int {
        val gpu = textures.getOrPut(tex) { GpuTexture(gl) }
        gpu.sync(tex, maxAnisotropy)
        gpu.lastUsed = frame
        return gpu.id
    }

    /** Frees GPU copies of scene objects that were not drawn for [EVICT_AFTER] frames. */
    private fun sweep() {
        val limit = frame - EVICT_AFTER
        geometries.entries.removeAll { (_, g) -> (g.lastUsed < limit).also { if (it) g.dispose() } }
        instances.entries.removeAll { (_, g) -> (g.lastUsed < limit).also { if (it) g.dispose() } }
        textures.entries.removeAll { (_, g) -> (g.lastUsed < limit).also { if (it) g.dispose() } }
        bounds.entries.removeAll { (_, b) -> b.lastUsed < limit }
        materialPrograms.entries.removeAll { (_, m) -> m.lastUsed < limit }
    }

    // ---------------------------------------------------------------- targets

    override fun createRenderTarget(width: Int, height: Int): RenderTarget = createRenderTarget(width, height, 0)

    override fun createRenderTarget(width: Int, height: Int, samples: Int): RenderTarget {
        if (generation == 0) onContextCreated()
        val t = GlRenderTarget(this, width, height, samples.coerceAtLeast(0))
        t.ensure(gl)
        targets.add(t)
        return t
    }

    internal fun forget(target: GlRenderTarget) {
        targets.remove(target)
    }

    override fun readPixels(target: RenderTarget): IntArray {
        val t = target as GlRenderTarget
        t.ensure(gl)
        t.resolve(gl)
        val w = t.width
        val h = t.height
        val raw = ByteArray(w * h * 4)
        gl.bindFramebuffer(GL.FRAMEBUFFER, t.readFbo)
        gl.readPixels(0, 0, w, h, raw)
        gl.bindFramebuffer(GL.FRAMEBUFFER, 0)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val src = (h - 1 - y) * w * 4
            for (x in 0 until w) {
                val i = src + x * 4
                val r = raw[i].toInt() and 0xFF
                val g = raw[i + 1].toInt() and 0xFF
                val b = raw[i + 2].toInt() and 0xFF
                val a = raw[i + 3].toInt() and 0xFF
                out[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }

    override fun dispose() {
        programs.values.forEach { it.dispose() }
        geometries.values.forEach { it.dispose() }
        instances.values.forEach { it.dispose() }
        textures.values.forEach { it.dispose() }
        depthProgram?.dispose()
        depthInstancedProgram?.dispose()
        presentProgram?.dispose()
        shadowMap.dispose()
        sceneBuffer?.dispose()
        targets.toList().forEach { it.dispose() }
        if (dfgTexture != 0) gl.deleteTexture(dfgTexture)
        if (emptyVao != 0) gl.deleteVertexArray(emptyVao)
        programs.clear()
        materialPrograms.clear()
        geometries.clear()
        instances.clear()
        textures.clear()
        bounds.clear()
        depthProgram = null
        depthInstancedProgram = null
        presentProgram = null
        sceneBuffer = null
        dfgTexture = 0
        emptyVao = 0
        generation++
    }

    private class RenderItem {
        lateinit var mesh: Mesh
        var z = 0f
        var renderOrder = 0
        var index = 0
    }

    private class MaterialProgram(
        val flags: Int,
        val patch: Any?,
        val extrasKey: String,
        val program: GlProgram,
        val generation: Int,
    ) {
        var lastUsed = 0
    }

    internal companion object {
        const val F_INSTANCED = 1
        const val F_INSTANCED_COLOR = 1 shl 1
        const val F_VERTEX_COLOR = 1 shl 2
        const val F_MAP = 1 shl 3
        const val F_FOG = 1 shl 4
        const val F_LIT = 1 shl 5
        const val F_FLAT = 1 shl 6
        const val F_DOUBLE = 1 shl 7
        const val F_FLIP = 1 shl 8
        const val F_OPAQUE = 1 shl 9
        const val F_SHADOWMAP = 1 shl 10

        private val DEFINE_NAMES = listOf(
            "INSTANCED", "INSTANCED_COLOR", "USE_VERTEX_COLOR", "USE_MAP", "USE_FOG", "LIT",
            "FLAT_SHADED", "DOUBLE_SIDED", "FLIP_SIDED", "OPAQUE", "USE_SHADOWMAP",
        )

        fun defineNames(flags: Int): List<String> = DEFINE_NAMES.filterIndexed { i, _ -> flags and (1 shl i) != 0 }

        /** three.js `LightShadow` defaults (radius 1, intensity 1). */
        const val SHADOW_RADIUS = 1f
        const val SHADOW_INTENSITY = 1f
        const val MAX_SHADOW_MAP = 4096
        const val FIRST_USER_TEXTURE_UNIT = 3
        const val SWEEP_INTERVAL = 600
        const val EVICT_AFTER = 1800

        private const val PRESENT_VS = ShaderLib.HEADER + """
out vec2 vUv;
void main() {
    vec2 p = vec2( float( ( gl_VertexID << 1 ) & 2 ), float( gl_VertexID & 2 ) );
    vUv = p;
    gl_Position = vec4( p * 2.0 - 1.0, 0.0, 1.0 );
}
"""
        private const val PRESENT_FS = ShaderLib.HEADER + """
in vec2 vUv;
uniform sampler2D src;
layout(location = 0) out vec4 fragColor;
void main() { fragColor = texture( src, vUv ); }
"""
    }
}
