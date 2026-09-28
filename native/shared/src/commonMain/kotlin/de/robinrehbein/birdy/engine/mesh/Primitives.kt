package de.robinrehbein.birdy.engine.mesh

import de.robinrehbein.birdy.engine.scene.Geometry
import kotlin.math.PI

/**
 * Procedural primitive generators matching three.js geometry constructors: same parameter order and
 * defaults, vertex order, normals, UVs and index order. Values are computed in Double (like JS) and
 * stored as Float (like `Float32BufferAttribute`).
 *
 * Indexed: box, plane, sphere, cylinder, cone, torus, circle, ring, shape.
 * Non-indexed: polyhedra (icosahedron, dodecahedron) and extrude, like three.js.
 */
object Primitives {
    private const val TWO_PI = PI * 2

    /** three.js `BoxGeometry`. */
    fun box(
        width: Double = 1.0, height: Double = 1.0, depth: Double = 1.0,
        widthSegments: Int = 1, heightSegments: Int = 1, depthSegments: Int = 1,
    ): Geometry = buildBox(width, height, depth, widthSegments, heightSegments, depthSegments)

    /** Float convenience overload of [box] with one segment per side. */
    fun box(width: Float, height: Float, depth: Float): Geometry =
        buildBox(width.toDouble(), height.toDouble(), depth.toDouble(), 1, 1, 1)

    /** three.js `PlaneGeometry` (XY plane, facing +Z). */
    fun plane(width: Double = 1.0, height: Double = 1.0, widthSegments: Int = 1, heightSegments: Int = 1): Geometry =
        buildPlane(width, height, widthSegments, heightSegments)

    /** three.js `SphereGeometry`. */
    fun sphere(
        radius: Double = 1.0, widthSegments: Int = 32, heightSegments: Int = 16,
        phiStart: Double = 0.0, phiLength: Double = TWO_PI, thetaStart: Double = 0.0, thetaLength: Double = PI,
    ): Geometry = buildSphere(radius, widthSegments, heightSegments, phiStart, phiLength, thetaStart, thetaLength)

    /** three.js `CylinderGeometry`. */
    fun cylinder(
        radiusTop: Double = 1.0, radiusBottom: Double = 1.0, height: Double = 1.0,
        radialSegments: Int = 32, heightSegments: Int = 1, openEnded: Boolean = false,
        thetaStart: Double = 0.0, thetaLength: Double = TWO_PI,
    ): Geometry = buildCylinder(radiusTop, radiusBottom, height, radialSegments, heightSegments, openEnded, thetaStart, thetaLength)

    /** three.js `ConeGeometry` (a cylinder with radiusTop = 0, apex at +height/2). */
    fun cone(
        radius: Double = 1.0, height: Double = 1.0, radialSegments: Int = 32, heightSegments: Int = 1,
        openEnded: Boolean = false, thetaStart: Double = 0.0, thetaLength: Double = TWO_PI,
    ): Geometry = buildCylinder(0.0, radius, height, radialSegments, heightSegments, openEnded, thetaStart, thetaLength)

    /** three.js `TorusGeometry` (ring in the XY plane around Z). */
    fun torus(
        radius: Double = 1.0, tube: Double = 0.4, radialSegments: Int = 12, tubularSegments: Int = 48,
        arc: Double = TWO_PI, thetaStart: Double = 0.0, thetaLength: Double = TWO_PI,
    ): Geometry = buildTorus(radius, tube, radialSegments, tubularSegments, arc, thetaStart, thetaLength)

    /** three.js `CircleGeometry` (XY plane). */
    fun circle(radius: Double = 1.0, segments: Int = 32, thetaStart: Double = 0.0, thetaLength: Double = TWO_PI): Geometry =
        buildCircle(radius, segments, thetaStart, thetaLength)

    /** three.js `RingGeometry` (XY plane). */
    fun ring(
        innerRadius: Double = 0.5, outerRadius: Double = 1.0, thetaSegments: Int = 32, phiSegments: Int = 1,
        thetaStart: Double = 0.0, thetaLength: Double = TWO_PI,
    ): Geometry = buildRing(innerRadius, outerRadius, thetaSegments, phiSegments, thetaStart, thetaLength)

    /** three.js `PolyhedronGeometry` from flat xyz [vertices] and triangle [indices]. */
    fun polyhedron(vertices: DoubleArray, indices: IntArray, radius: Double = 1.0, detail: Int = 0): Geometry =
        buildPolyhedron(vertices, indices, radius, detail)

    /** three.js `IcosahedronGeometry`. */
    fun icosahedron(radius: Double = 1.0, detail: Int = 0): Geometry =
        buildPolyhedron(ICOSAHEDRON_VERTICES, ICOSAHEDRON_INDICES, radius, detail)

    /** three.js `DodecahedronGeometry`. */
    fun dodecahedron(radius: Double = 1.0, detail: Int = 0): Geometry =
        buildPolyhedron(DODECAHEDRON_VERTICES, DODECAHEDRON_INDICES, radius, detail)

    /** three.js `ShapeGeometry`. */
    fun shape(shape: Shape, curveSegments: Int = 12): Geometry = buildShapeGeometry(listOf(shape), curveSegments)

    fun shapes(shapes: List<Shape>, curveSegments: Int = 12): Geometry = buildShapeGeometry(shapes, curveSegments)

    /** three.js `ExtrudeGeometry` (straight extrusion along +Z, optional bevel). */
    fun extrude(shape: Shape, options: ExtrudeOptions = ExtrudeOptions()): Geometry = buildExtrude(listOf(shape), options)

    fun extrude(shapes: List<Shape>, options: ExtrudeOptions = ExtrudeOptions()): Geometry = buildExtrude(shapes, options)
}
