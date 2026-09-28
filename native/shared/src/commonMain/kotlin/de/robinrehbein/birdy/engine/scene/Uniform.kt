package de.robinrehbein.birdy.engine.scene

import de.robinrehbein.birdy.engine.math.Color
import de.robinrehbein.birdy.engine.math.Mat4
import de.robinrehbein.birdy.engine.math.Vec3

/** A shader uniform value owned by a material; mutate `value` per frame, the backend uploads it. */
sealed class Uniform {
    class F(var value: Float) : Uniform()
    class V3(val value: Vec3) : Uniform()
    class C(val value: Color) : Uniform()
    class M4(val value: Mat4) : Uniform()
    class Tex(var value: Texture?) : Uniform()
}

/**
 * Shader-code injection for built-in materials, the equivalent of three.js `onBeforeCompile`
 * patches (skin FX, cactus bristle, sky haze). The backend inserts each chunk at a fixed hook in
 * its standard shader; see docs/native/ARCHITECTURE.md "Shader hooks" for the variables in scope.
 *
 * Hooks (all optional, GLSL ES 3.00):
 *  - [vertexHead] / [fragmentHead]: declarations (uniforms, varyings, functions).
 *  - [vertexBody]: runs after `vec3 transformed` (object-space position) and `vec3 objectNormal`
 *    are set, before the model-view transform.
 *  - [fragmentColor]: may modify `vec4 diffuseColor` and `vec3 totalEmissive` before lighting.
 *  - [fragmentOutput]: may modify `vec3 outgoingLight` after lighting, before fog.
 *
 * Programs are cached by [key]; two patches with the same key must have identical code.
 */
class ShaderPatch(
    val key: String,
    val vertexHead: String = "",
    val vertexBody: String = "",
    val fragmentHead: String = "",
    val fragmentColor: String = "",
    val fragmentOutput: String = "",
    val uniforms: MutableMap<String, Uniform> = LinkedHashMap(),
)
