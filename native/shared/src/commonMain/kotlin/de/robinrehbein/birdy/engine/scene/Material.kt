package de.robinrehbein.birdy.engine.scene

import de.robinrehbein.birdy.engine.math.Color

enum class Side { Front, Back, Double }
enum class Blending { Normal, Additive }

/**
 * Base material state shared by all shading models (three.js `Material`).
 * Changing any field takes effect on the next frame; no explicit `needsUpdate` required except
 * for [patch] replacement, which selects a different cached program automatically.
 */
sealed class Material {
    val color = Color(1f, 1f, 1f)
    var opacity = 1f
    var transparent = false
    var depthTest = true
    var depthWrite = true
    var side = Side.Front
    var blending = Blending.Normal
    var vertexColors = false
    /** Whether scene fog applies. */
    var fog = true
    var map: Texture? = null
    var visible = true
    /** Optional onBeforeCompile-style shader code injection. */
    var patch: ShaderPatch? = null
    /** Opaque per-material bag for view code (three.js `material.userData`, e.g. `ownGlow`). */
    val userData = HashMap<String, Any>()
}

/** Unlit colour (three.js `MeshBasicMaterial`). */
class BasicMaterial : Material()

/**
 * Lit material approximating three.js `MeshStandardMaterial` (hemisphere + directional light,
 * shadows, emissive). The GL backend implements a compact GGX/Lambert model tuned to match the
 * JS reference screenshots, not a bit-exact PBR port.
 */
class StandardMaterial : Material() {
    var roughness = 1f
    var metalness = 0f
    val emissive = Color(0f, 0f, 0f)
    var emissiveIntensity = 1f
    /** Faceted normals from screen-space derivatives (three.js `flatShading`). */
    var flatShading = false
}

/**
 * Fully custom program (three.js `ShaderMaterial`, used by the sky dome). Sources are GLSL ES
 * 3.00 bodies without the `#version` line; the backend prepends the version, precision and the
 * standard attributes/uniforms documented in ARCHITECTURE.md.
 */
class CustomShaderMaterial(
    val key: String,
    val vertexSource: String,
    val fragmentSource: String,
    val uniforms: MutableMap<String, Uniform> = LinkedHashMap(),
) : Material()
