package de.robinrehbein.birdy.engine.mesh

import de.robinrehbein.birdy.engine.scene.Geometry

/** three.js `ShapeGeometry`: flat triangulated shapes in the XY plane, world-space UVs. */
internal fun buildShapeGeometry(shapes: List<Shape>, curveSegments: Int): Geometry {
    val pos = DoubleList(); val nor = DoubleList(); val uv = DoubleList(); val idx = IntList()
    for (shape in shapes) {
        val indexOffset = pos.size / 3
        val points = shape.extractPoints(curveSegments)
        var shapeVertices = points.shape
        val shapeHoles = points.holes
        if (!ShapeUtils.isClockWise(shapeVertices)) shapeVertices.reverse()
        for (i in shapeHoles.indices) if (ShapeUtils.isClockWise(shapeHoles[i])) shapeHoles[i].reverse()
        val faces = ShapeUtils.triangulateShape(shapeVertices, shapeHoles)
        val all = ArrayList(shapeVertices)
        for (h in shapeHoles) all.addAll(h)
        for (v in all) {
            pos.add(v.x, v.y, 0.0)
            nor.add(0.0, 0.0, 1.0)
            uv.add(v.x, v.y)
        }
        for (f in faces) idx.add(f[0] + indexOffset, f[1] + indexOffset, f[2] + indexOffset)
    }
    return indexedGeometry(pos, nor, uv, idx)
}
