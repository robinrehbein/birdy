package de.robinrehbein.birdy.engine.gl

/**
 * GLSL ES 3.00 fragments ported from three.js r186 shader chunks (common, bsdfs,
 * lights_physical_pars_fragment, shadowmap_pars_fragment, colorspace_pars_fragment).
 * Only the paths Birdy uses: one hemisphere light, one directional light with an optional PCF
 * shadow, no environment map, no tone mapping, sRGB output.
 */
internal object ShaderChunks {
    const val COMMON = """
#define PI 3.141592653589793
#define PI2 6.283185307179586
#define RECIPROCAL_PI 0.3183098861837907
#define EPSILON 1e-6
#define saturate( a ) clamp( a, 0.0, 1.0 )
float pow2( const in float x ) { return x * x; }
vec3 linearToSrgb( vec3 c ) {
    return mix( pow( c, vec3( 0.41666 ) ) * 1.055 - vec3( 0.055 ), c * 12.92, vec3( lessThanEqual( c, vec3( 0.0031308 ) ) ) );
}
vec3 srgbToLinear( vec3 c ) {
    return mix( pow( c * 0.9478672986 + vec3( 0.0521327014 ), vec3( 2.4 ) ), c * 0.0773993808, vec3( lessThanEqual( c, vec3( 0.04045 ) ) ) );
}
vec4 linearToOutputTexel( vec4 value ) { return vec4( linearToSrgb( value.rgb ), value.a ); }
"""

    const val PHYSICAL = """
uniform sampler2D dfgLUT;
vec3 BRDF_Lambert( const in vec3 diffuseColor ) { return RECIPROCAL_PI * diffuseColor; }
vec3 F_Schlick( const in vec3 f0, const in float f90, const in float dotVH ) {
    float fresnel = exp2( ( - 5.55473 * dotVH - 6.98316 ) * dotVH );
    return f0 * ( 1.0 - fresnel ) + ( f90 * fresnel );
}
float V_GGX_SmithCorrelated( const in float alpha, const in float dotNL, const in float dotNV ) {
    float a2 = pow2( alpha );
    float gv = dotNL * sqrt( a2 + ( 1.0 - a2 ) * pow2( dotNV ) );
    float gl = dotNV * sqrt( a2 + ( 1.0 - a2 ) * pow2( dotNL ) );
    return 0.5 / max( gv + gl, EPSILON );
}
float D_GGX( const in float alpha, const in float dotNH ) {
    float a2 = pow2( alpha );
    float denom = pow2( dotNH ) * ( a2 - 1.0 ) + 1.0;
    return RECIPROCAL_PI * a2 / pow2( denom );
}
vec3 BRDF_GGX( const in vec3 lightDir, const in vec3 viewDir, const in vec3 normal, const in vec3 f0, const in float f90, const in float roughness ) {
    float alpha = pow2( roughness );
    vec3 halfDir = normalize( lightDir + viewDir );
    float dotNL = saturate( dot( normal, lightDir ) );
    float dotNV = saturate( dot( normal, viewDir ) );
    float dotNH = saturate( dot( normal, halfDir ) );
    float dotVH = saturate( dot( viewDir, halfDir ) );
    vec3 F = F_Schlick( f0, f90, dotVH );
    float V = V_GGX_SmithCorrelated( alpha, dotNL, dotNV );
    float D = D_GGX( alpha, dotNH );
    return F * ( V * D );
}
void computeMultiscattering( const in vec2 fab, const in vec3 specularColor, const in float specularF90, inout vec3 singleScatter, inout vec3 multiScatter ) {
    vec3 Fr = specularColor;
    vec3 FssEss = Fr * fab.x + specularF90 * fab.y;
    float Ess = fab.x + fab.y;
    float Ems = 1.0 - Ess;
    vec3 Favg = Fr + ( 1.0 - Fr ) * 0.047619;
    vec3 Fms = FssEss * Favg / ( 1.0 - Ems * Favg );
    singleScatter += FssEss;
    multiScatter += Fms * Ems;
}
"""

    const val SHADOW = """
uniform highp sampler2DShadow shadowMap;
uniform vec2 shadowMapSize;
uniform float shadowBias;
uniform float shadowRadius;
uniform float shadowIntensity;
uniform bool receiveShadow;
in vec4 vShadowCoord;
float interleavedGradientNoise( vec2 position ) {
    return fract( 52.9829189 * fract( dot( position, vec2( 0.06711056, 0.00583715 ) ) ) );
}
vec2 vogelDiskSample( int sampleIndex, int samplesCount, float phi ) {
    const float goldenAngle = 2.399963229728653;
    float r = sqrt( ( float( sampleIndex ) + 0.5 ) / float( samplesCount ) );
    float theta = float( sampleIndex ) * goldenAngle + phi;
    return vec2( cos( theta ), sin( theta ) ) * r;
}
float getShadow( vec4 shadowCoord ) {
    float shadow = 1.0;
    shadowCoord.xyz /= shadowCoord.w;
    shadowCoord.z += shadowBias;
    bool inFrustum = shadowCoord.x >= 0.0 && shadowCoord.x <= 1.0 && shadowCoord.y >= 0.0 && shadowCoord.y <= 1.0;
    bool frustumTest = inFrustum && shadowCoord.z <= 1.0;
    if ( frustumTest ) {
        vec2 texelSize = vec2( 1.0 ) / shadowMapSize;
        float radius = shadowRadius * texelSize.x;
        float phi = interleavedGradientNoise( gl_FragCoord.xy ) * PI2;
        shadow = (
            texture( shadowMap, vec3( shadowCoord.xy + vogelDiskSample( 0, 5, phi ) * radius, shadowCoord.z ) ) +
            texture( shadowMap, vec3( shadowCoord.xy + vogelDiskSample( 1, 5, phi ) * radius, shadowCoord.z ) ) +
            texture( shadowMap, vec3( shadowCoord.xy + vogelDiskSample( 2, 5, phi ) * radius, shadowCoord.z ) ) +
            texture( shadowMap, vec3( shadowCoord.xy + vogelDiskSample( 3, 5, phi ) * radius, shadowCoord.z ) ) +
            texture( shadowMap, vec3( shadowCoord.xy + vogelDiskSample( 4, 5, phi ) * radius, shadowCoord.z ) )
        ) * 0.2;
    }
    return mix( 1.0, shadow, shadowIntensity );
}
"""

    /**
     * three.js `lights_physical_fragment` + `lights_fragment_begin/end` for one hemisphere and one
     * directional light, in view space. Expects `normal`, `diffuseColor`, `totalEmissive` in scope
     * and produces `outgoingLight`.
     */
    const val LIGHTS = """
    vec3 nonPerturbedNormal = normal;
    vec3 diffuseContribution = diffuseColor.rgb * ( 1.0 - metalness );
    vec3 dxy = max( abs( dFdx( nonPerturbedNormal ) ), abs( dFdy( nonPerturbedNormal ) ) );
    float geometryRoughness = max( max( dxy.x, dxy.y ), dxy.z );
    float matRoughness = max( roughness, 0.0525 );
    matRoughness += geometryRoughness;
    matRoughness = min( matRoughness, 1.0 );
    vec3 specularColor = vec3( 0.04 );
    vec3 specularColorBlended = mix( specularColor, diffuseColor.rgb, metalness );
    float specularF90 = 1.0;

    vec3 geometryViewDir = normalize( vViewPosition );
    float dotNVms = saturate( dot( normal, geometryViewDir ) );
    vec2 dfg = texture( dfgLUT, vec2( matRoughness, dotNVms ) ).rg;
    float EssMs = dfg.x + dfg.y;
    vec3 multiScatteringCompensation = 1.0 + specularColorBlended * ( 1.0 / EssMs - 1.0 );

    vec3 directDiffuse = vec3( 0.0 );
    vec3 directSpecular = vec3( 0.0 );
    vec3 indirectDiffuse = vec3( 0.0 );

    // Directional light (RE_Direct_Physical)
    vec3 sunLightColor = sunColor;
    #ifdef USE_SHADOWMAP
    sunLightColor *= receiveShadow ? getShadow( vShadowCoord ) : 1.0;
    #endif
    float dotNL = saturate( dot( normal, sunDirection ) );
    vec3 irradiance = dotNL * sunLightColor;
    directSpecular += irradiance * BRDF_GGX( sunDirection, geometryViewDir, normal, specularColorBlended, specularF90, matRoughness ) * multiScatteringCompensation;
    vec3 halfDir = normalize( sunDirection + geometryViewDir );
    float dotVH = saturate( dot( geometryViewDir, halfDir ) );
    vec3 F = F_Schlick( specularColor, specularF90, dotVH );
    directDiffuse += irradiance * BRDF_Lambert( diffuseContribution ) * ( 1.0 - F );

    // Hemisphere light (RE_IndirectDiffuse_Physical); no environment map, so no indirect specular.
    float hemiWeight = 0.5 * dot( normal, hemiDirection ) + 0.5;
    vec3 hemiIrradiance = mix( hemiGroundColor, hemiSkyColor, hemiWeight );
    vec3 singleScattering = vec3( 0.0 );
    vec3 multiScattering = vec3( 0.0 );
    computeMultiscattering( dfg, specularColor, specularF90, singleScattering, multiScattering );
    indirectDiffuse += hemiIrradiance * BRDF_Lambert( diffuseContribution ) * ( 1.0 - singleScattering - multiScattering );

    vec3 outgoingLight = directDiffuse + indirectDiffuse + directSpecular + totalEmissive;
"""
}
