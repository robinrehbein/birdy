package de.robinrehbein.birdy.engine.gl

/** OpenGL ES 3.0 enum values used by the renderer (identical on every platform). */
@Suppress("unused")
object GL {
    const val NO_ERROR = 0
    const val VENDOR = 0x1F00
    const val RENDERER = 0x1F01
    const val VERSION = 0x1F02
    const val SHADING_LANGUAGE_VERSION = 0x8B8C
    const val EXTENSIONS = 0x1F03
    const val NONE = 0
    const val MAX_SAMPLES = 0x8D57
    const val MAX_TEXTURE_SIZE = 0x0D33
    const val MAX_VERTEX_ATTRIBS = 0x8869
    const val TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE
    const val MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF
    const val CONTEXT_LOST = 0x0507

    const val DEPTH_BUFFER_BIT = 0x00000100
    const val STENCIL_BUFFER_BIT = 0x00000400
    const val COLOR_BUFFER_BIT = 0x00004000

    const val POINTS = 0x0000
    const val LINES = 0x0001
    const val TRIANGLES = 0x0004
    const val TRIANGLE_STRIP = 0x0005

    const val ZERO = 0
    const val ONE = 1
    const val SRC_ALPHA = 0x0302
    const val ONE_MINUS_SRC_ALPHA = 0x0303
    const val FUNC_ADD = 0x8006

    const val FRONT = 0x0404
    const val BACK = 0x0405
    const val FRONT_AND_BACK = 0x0408
    const val CW = 0x0900
    const val CCW = 0x0901

    const val CULL_FACE = 0x0B44
    const val DEPTH_TEST = 0x0B71
    const val BLEND = 0x0BE2
    const val POLYGON_OFFSET_FILL = 0x8037
    const val SCISSOR_TEST = 0x0C11

    const val NEVER = 0x0200
    const val LESS = 0x0201
    const val EQUAL = 0x0202
    const val LEQUAL = 0x0203
    const val ALWAYS = 0x0207

    const val BYTE = 0x1400
    const val UNSIGNED_BYTE = 0x1401
    const val SHORT = 0x1402
    const val UNSIGNED_SHORT = 0x1403
    const val INT = 0x1404
    const val UNSIGNED_INT = 0x1405
    const val FLOAT = 0x1406
    const val HALF_FLOAT = 0x140B

    const val DEPTH_COMPONENT = 0x1902
    const val RGB = 0x1907
    const val RGBA = 0x1908
    const val RGBA8 = 0x8058
    const val RG = 0x8227
    const val RG16F = 0x822F
    const val SRGB8_ALPHA8 = 0x8C43
    const val DEPTH_COMPONENT16 = 0x81A5
    const val DEPTH_COMPONENT24 = 0x81A6
    const val DEPTH_COMPONENT32F = 0x8CAC
    const val DEPTH24_STENCIL8 = 0x88F0

    const val ARRAY_BUFFER = 0x8892
    const val ELEMENT_ARRAY_BUFFER = 0x8893
    const val STATIC_DRAW = 0x88E4
    const val DYNAMIC_DRAW = 0x88E8
    const val STREAM_DRAW = 0x88E0

    const val FRAGMENT_SHADER = 0x8B30
    const val VERTEX_SHADER = 0x8B31
    const val COMPILE_STATUS = 0x8B81
    const val LINK_STATUS = 0x8B82

    const val TEXTURE_2D = 0x0DE1
    const val TEXTURE0 = 0x84C0
    const val TEXTURE1 = 0x84C1
    const val TEXTURE2 = 0x84C2
    const val TEXTURE3 = 0x84C3
    const val TEXTURE_MAG_FILTER = 0x2800
    const val TEXTURE_MIN_FILTER = 0x2801
    const val TEXTURE_WRAP_S = 0x2802
    const val TEXTURE_WRAP_T = 0x2803
    const val TEXTURE_COMPARE_MODE = 0x884C
    const val TEXTURE_COMPARE_FUNC = 0x884D
    const val COMPARE_REF_TO_TEXTURE = 0x884E
    const val NEAREST = 0x2600
    const val LINEAR = 0x2601
    const val LINEAR_MIPMAP_LINEAR = 0x2703
    const val REPEAT = 0x2901
    const val CLAMP_TO_EDGE = 0x812F
    const val MIRRORED_REPEAT = 0x8370

    const val FRAMEBUFFER = 0x8D40
    const val READ_FRAMEBUFFER = 0x8CA8
    const val DRAW_FRAMEBUFFER = 0x8CA9
    const val RENDERBUFFER = 0x8D41
    const val COLOR_ATTACHMENT0 = 0x8CE0
    const val DEPTH_ATTACHMENT = 0x8D00
    const val DEPTH_STENCIL_ATTACHMENT = 0x821A
    const val FRAMEBUFFER_COMPLETE = 0x8CD5
}
