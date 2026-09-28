package de.robinrehbein.birdy.engine.gl

import de.robinrehbein.birdy.engine.scene.ShaderPatch

/**
 * GLSL ES 3.00 sources for the built-in shading models, following the three.js r186 chunk order
 * (map -> vertex colour -> [fragmentColor hook] -> lighting -> [fragmentOutput hook] -> opaque
 * alpha -> sRGB encode -> fog, with the fog colour in output space like three.js on-screen).
 * Attribute locations are fixed, see [Attr].
 *
 * Defines: INSTANCED, INSTANCED_COLOR, USE_VERTEX_COLOR, USE_MAP, USE_FOG, LIT, FLAT_SHADED,
 * DOUBLE_SIDED, FLIP_SIDED, OPAQUE, USE_SHADOWMAP.
 *
 * Variables available to [ShaderPatch] hooks (in addition to the uniforms below):
 *  - vertexBody: `vec3 transformed`, `vec3 objectNormal` (object space, both writable),
 *    attributes `position normal color uv` (+ `instanceMatrix`/`instanceColor` when instanced,
 *    + geometry extra attributes by name), uniforms `modelMatrix viewMatrix projectionMatrix
 *    modelViewMatrix normalMatrix cameraPosition`; writable varyings `vColor vUv`.
 *  - fragmentColor: `vec4 diffuseColor` (linear, after map and vertex colour), `vec3 totalEmissive`
 *    (linear radiance, added after lighting), varyings `vWorldPosition vViewPosition vNormal`
 *    (view space, not normalized) `vColor vUv`, uniforms `cameraPosition viewMatrix`.
 *  - fragmentOutput: `vec3 outgoingLight` (linear, lit), `vec4 diffuseColor`; for lit materials
 *    also `vec3 normal` (view space, final) and `vec3 geometryViewDir`.
 */
internal object ShaderLib {
    const val HEADER = "#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\n"

    private fun StringBuilder.defines(defines: List<String>) {
        defines.forEach { append("#define ").append(it).append('\n') }
    }

    private const val VERTEX_DECLS = """
layout(location = 0) in vec3 position;
layout(location = 1) in vec3 normal;
layout(location = 2) in vec3 color;
layout(location = 3) in vec2 uv;
#ifdef INSTANCED
layout(location = 4) in mat4 instanceMatrix;
#endif
#ifdef INSTANCED_COLOR
layout(location = 8) in vec3 instanceColor;
#endif
uniform mat4 modelMatrix;
uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelViewMatrix;
uniform mat3 normalMatrix;
uniform vec3 cameraPosition;
"""

    fun vertex(defines: List<String>, patch: ShaderPatch?): String = buildString {
        append(HEADER)
        defines(defines)
        append(VERTEX_DECLS)
        append(
            """
#ifdef USE_MAP
uniform vec4 uvTransform;
#endif
#ifdef USE_SHADOWMAP
uniform mat4 shadowMatrix;
uniform float shadowNormalBias;
out vec4 vShadowCoord;
#endif
out vec3 vWorldPosition;
out vec3 vViewPosition;
out vec3 vNormal;
out vec3 vColor;
out vec2 vUv;
out vec2 vMapUv;
out float vFogDepth;
""",
        )
        append('\n').append(patch?.vertexHead.orEmpty()).append('\n')
        append(
            """
void main() {
    vec3 objectNormal = normal;
    vec3 transformed = position;
    vColor = vec3( 1.0 );
    #ifdef USE_VERTEX_COLOR
    vColor *= color;
    #endif
    #ifdef INSTANCED_COLOR
    vColor *= instanceColor;
    #endif
    vUv = uv;
""",
        )
        append('\n').append(patch?.vertexBody.orEmpty()).append('\n')
        append(
            """
    #ifdef USE_MAP
    vMapUv = vUv * uvTransform.xy + uvTransform.zw;
    #else
    vMapUv = vUv;
    #endif
    vec3 transformedNormal = objectNormal;
    #ifdef INSTANCED
    mat3 im = mat3( instanceMatrix );
    transformedNormal /= vec3( dot( im[ 0 ], im[ 0 ] ), dot( im[ 1 ], im[ 1 ] ), dot( im[ 2 ], im[ 2 ] ) );
    transformedNormal = im * transformedNormal;
    #endif
    transformedNormal = normalMatrix * transformedNormal;
    #ifdef FLIP_SIDED
    transformedNormal = - transformedNormal;
    #endif
    vNormal = normalize( transformedNormal );

    vec4 localPosition = vec4( transformed, 1.0 );
    #ifdef INSTANCED
    localPosition = instanceMatrix * localPosition;
    #endif
    vec4 mvPosition = modelViewMatrix * localPosition;
    gl_Position = projectionMatrix * mvPosition;
    vViewPosition = - mvPosition.xyz;
    vec4 worldPosition = modelMatrix * localPosition;
    vWorldPosition = worldPosition.xyz;
    #ifdef USE_SHADOWMAP
    vec3 shadowWorldNormal = normalize( ( vec4( transformedNormal, 0.0 ) * viewMatrix ).xyz );
    vShadowCoord = shadowMatrix * ( worldPosition + vec4( shadowWorldNormal * shadowNormalBias, 0.0 ) );
    #endif
    vFogDepth = - mvPosition.z;
}
""",
        )
    }

    fun fragment(defines: List<String>, patch: ShaderPatch?): String = buildString {
        append(HEADER)
        defines(defines)
        append(
            """
in vec3 vWorldPosition;
in vec3 vViewPosition;
in vec3 vNormal;
in vec3 vColor;
in vec2 vUv;
in vec2 vMapUv;
in float vFogDepth;
layout(location = 0) out vec4 fragColor;
uniform mat4 viewMatrix;
uniform vec3 cameraPosition;
uniform vec3 diffuse;
uniform float opacity;
uniform vec3 emissive;
uniform float roughness;
uniform float metalness;
uniform vec3 hemiSkyColor;
uniform vec3 hemiGroundColor;
uniform vec3 hemiDirection;
uniform vec3 sunColor;
uniform vec3 sunDirection;
#ifdef USE_FOG
uniform vec3 fogColor;
uniform float fogNear;
uniform float fogFar;
#endif
#ifdef USE_MAP
uniform sampler2D map;
#endif
""",
        )
        append(ShaderChunks.COMMON)
        append("#ifdef LIT\n").append(ShaderChunks.PHYSICAL).append("#endif\n")
        append("#if defined( LIT ) && defined( USE_SHADOWMAP )\n").append(ShaderChunks.SHADOW).append("#endif\n")
        append('\n').append(patch?.fragmentHead.orEmpty()).append('\n')
        append(
            """
void main() {
    vec4 diffuseColor = vec4( diffuse, opacity );
    vec3 totalEmissive = emissive;
    #ifdef USE_MAP
    diffuseColor *= texture( map, vMapUv );
    #endif
    #if defined( USE_VERTEX_COLOR ) || defined( INSTANCED_COLOR )
    diffuseColor.rgb *= vColor;
    #endif
""",
        )
        append('\n').append(patch?.fragmentColor.orEmpty()).append('\n')
        append(
            """
    #ifdef LIT
    float faceDirection = gl_FrontFacing ? 1.0 : - 1.0;
    #ifdef FLAT_SHADED
    vec3 normal = normalize( cross( dFdx( vViewPosition ), dFdy( vViewPosition ) ) );
    #else
    vec3 normal = normalize( vNormal );
    #ifdef DOUBLE_SIDED
    normal *= faceDirection;
    #endif
    #endif
""",
        )
        append(ShaderChunks.LIGHTS)
        append(
            """
    #else
    vec3 outgoingLight = diffuseColor.rgb;
    #endif
""",
        )
        append('\n').append(patch?.fragmentOutput.orEmpty()).append('\n')
        append(
            """
    #ifdef OPAQUE
    diffuseColor.a = 1.0;
    #endif
    fragColor = linearToOutputTexel( vec4( outgoingLight, diffuseColor.a ) );
    #ifdef USE_FOG
    float fogFactor = smoothstep( fogNear, fogFar, vFogDepth );
    fragColor.rgb = mix( fragColor.rgb, fogColor, fogFactor );
    #endif
}
""",
        )
    }

    /** Depth-only program for the directional shadow map (three.js `MeshDepthMaterial`). */
    fun depthVertex(instanced: Boolean): String = buildString {
        append(HEADER)
        if (instanced) append("#define INSTANCED\n")
        append(
            """
layout(location = 0) in vec3 position;
#ifdef INSTANCED
layout(location = 4) in mat4 instanceMatrix;
#endif
uniform mat4 modelMatrix;
uniform mat4 lightViewProjection;
void main() {
    vec4 p = vec4( position, 1.0 );
    #ifdef INSTANCED
    p = instanceMatrix * p;
    #endif
    gl_Position = lightViewProjection * ( modelMatrix * p );
}
""",
        )
    }

    fun depthFragment(): String = HEADER + "void main() {}\n"

    /**
     * Wraps a [de.robinrehbein.birdy.engine.scene.CustomShaderMaterial] body. Prepended to the
     * vertex source: defines, attributes `position normal color uv` (+ `instanceMatrix`), uniforms
     * `modelMatrix viewMatrix projectionMatrix modelViewMatrix normalMatrix cameraPosition`.
     * Prepended to the fragment source: defines, `out vec4 fragColor`, uniform `cameraPosition`,
     * helpers `linearToSrgb srgbToLinear linearToOutputTexel`, macros `PI saturate`, and with
     * USE_FOG the uniforms `fogColor fogNear fogFar`. Colour uniforms
     * ([de.robinrehbein.birdy.engine.scene.Uniform.C]) arrive linear, so a custom shader ends with
     * `fragColor = linearToOutputTexel(...)` like three.js `#include <colorspace_fragment>`.
     */
    fun customVertex(source: String, defines: List<String>): String = buildString {
        append(HEADER)
        defines(defines)
        append(VERTEX_DECLS)
        append(source)
    }

    fun customFragment(source: String, defines: List<String>): String = buildString {
        append(HEADER)
        defines(defines)
        append("layout(location = 0) out vec4 fragColor;\nuniform vec3 cameraPosition;\n")
        append("#ifdef USE_FOG\nuniform vec3 fogColor;\nuniform float fogNear;\nuniform float fogFar;\n#endif\n")
        append(ShaderChunks.COMMON)
        append(source)
    }
}

/** Fixed vertex attribute locations shared by all programs. */
internal object Attr {
    const val POSITION = 0
    const val NORMAL = 1
    const val COLOR = 2
    const val UV = 3
    /** mat4 occupies locations 4..7. */
    const val INSTANCE_MATRIX = 4
    const val INSTANCE_COLOR = 8
    /** First location available to [de.robinrehbein.birdy.engine.scene.Geometry.extraAttributes]. */
    const val FIRST_EXTRA = 9
}
