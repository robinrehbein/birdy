package de.robinrehbein.birdy.engine.scene

import de.robinrehbein.birdy.engine.math.Euler
import de.robinrehbein.birdy.engine.math.Mat4
import de.robinrehbein.birdy.engine.math.Quat
import de.robinrehbein.birdy.engine.math.Vec3

/**
 * Scene-graph node (three.js `Object3D` equivalent). Transform = translate * rotate * scale.
 *
 * Rotation is kept as XYZ Euler angles ([rotation], radians) unless [quaternionOverride] is set,
 * which is how billboards copy the camera orientation (`marker.quaternion.copy(camera.quaternion)`).
 * Quaternion-based helpers ([setQuaternion], [rotateOnAxis], [lookAt]) write the result back into
 * whichever representation is active, so [rotation] stays valid like three.js keeps Euler and quaternion in sync.
 * Only the owning thread (the GL/game thread) may mutate a node.
 */
open class Node(var name: String = "") {
    val position = Vec3()
    val rotation = Vec3()
    val scale = Vec3(1f, 1f, 1f)
    /** When non-null, used instead of [rotation]. */
    var quaternionOverride: Quat? = null

    /** Up direction used by [lookAt] (three.js `Object3D.up`). */
    val up = Vec3(0f, 1f, 0f)

    var visible = true
    /** Draw-order hint within the same pass (three.js `renderOrder`); higher draws later. */
    var renderOrder = 0
    var castShadow = false
    var receiveShadow = false
    /** When false, [localMatrix] is left as set by the caller (baked transforms). */
    var matrixAutoUpdate = true

    val localMatrix = Mat4()
    val worldMatrix = Mat4()

    /** Free-form per-node data for view code (three.js `object.userData`). */
    val userData: MutableMap<String, Any> by lazy { HashMap() }

    var parent: Node? = null
        private set
    private val _children = ArrayList<Node>()
    val children: List<Node> get() = _children

    fun add(vararg nodes: Node): Node {
        for (n in nodes) {
            if (n === this) continue
            n.parent?.remove(n)
            n.parent = this
            _children.add(n)
        }
        return this
    }

    fun remove(node: Node) {
        if (_children.remove(node)) node.parent = null
    }

    /** Detaches this node from its parent, if any (three.js `removeFromParent`). */
    fun removeFromParent(): Node {
        parent?.remove(this)
        return this
    }

    fun clear() {
        for (c in _children) c.parent = null
        _children.clear()
    }

    /** Depth-first visit of this node and all descendants (including invisible ones). */
    fun traverse(visit: (Node) -> Unit) {
        visit(this)
        for (c in _children) c.traverse(visit)
    }

    /** Like [traverse] but skips invisible nodes and their subtrees (three.js `traverseVisible`). */
    fun traverseVisible(visit: (Node) -> Unit) {
        if (!visible) return
        visit(this)
        for (c in _children) c.traverseVisible(visit)
    }

    /** Visits all ancestors, nearest first (three.js `traverseAncestors`). */
    fun traverseAncestors(visit: (Node) -> Unit) {
        var p = parent
        while (p != null) { visit(p); p = p.parent }
    }

    /** First node in this subtree with the given [name] (depth-first). */
    fun findByName(name: String): Node? {
        if (this.name == name) return this
        for (c in _children) c.findByName(name)?.let { return it }
        return null
    }

    private val tmpQuat = Quat()

    fun updateLocalMatrix() {
        val q = quaternionOverride ?: tmpQuat.setFromEulerXYZ(rotation.x, rotation.y, rotation.z)
        localMatrix.compose(position, q, scale)
    }

    /** Recomputes world matrices of this subtree; call once per frame on the scene root. */
    fun updateWorldMatrix(parentWorld: Mat4?) {
        if (matrixAutoUpdate) updateLocalMatrix()
        if (parentWorld == null) worldMatrix.set(localMatrix) else worldMatrix.multiply(parentWorld, localMatrix)
        for (c in _children) c.updateWorldMatrix(worldMatrix)
    }

    /**
     * three.js `updateWorldMatrix(updateParents, updateChildren)`: optionally refreshes the ancestor
     * chain first, then this node, then optionally the whole subtree.
     */
    fun updateWorldMatrix(updateParents: Boolean, updateChildren: Boolean) {
        val p = parent
        if (updateParents && p != null) p.updateWorldMatrix(true, false)
        if (matrixAutoUpdate) updateLocalMatrix()
        if (p == null) worldMatrix.set(localMatrix) else worldMatrix.multiply(p.worldMatrix, localMatrix)
        if (updateChildren) for (c in _children) c.updateWorldMatrix(worldMatrix)
    }

    /** Local orientation as a quaternion (three.js `object.quaternion`). */
    fun getQuaternion(target: Quat = Quat()): Quat =
        quaternionOverride?.let { target.set(it) } ?: target.setFromEulerXYZ(rotation.x, rotation.y, rotation.z)

    /** Sets the local orientation; stored in [quaternionOverride] if present, otherwise as XYZ Euler. */
    fun setQuaternion(q: Quat): Node {
        val o = quaternionOverride
        if (o != null) o.set(q) else Euler.fromQuat(q, rotation)
        return this
    }

    /** three.js `rotateOnAxis`: rotates in local space around the normalized [axis]. */
    fun rotateOnAxis(axis: Vec3, angle: Float): Node {
        val q = getQuaternion(Quat()).multiply(Quat().setFromAxisAngle(axis, angle))
        return setQuaternion(q)
    }

    /** three.js `rotateOnWorldAxis` (assumes no rotated parent, like three.js). */
    fun rotateOnWorldAxis(axis: Vec3, angle: Float): Node {
        val q = getQuaternion(Quat()).premultiply(Quat().setFromAxisAngle(axis, angle))
        return setQuaternion(q)
    }

    fun rotateX(angle: Float): Node = rotateOnAxis(X_AXIS, angle)
    fun rotateY(angle: Float): Node = rotateOnAxis(Y_AXIS, angle)
    fun rotateZ(angle: Float): Node = rotateOnAxis(Z_AXIS, angle)

    /** Moves along a local axis (three.js `translateOnAxis`). */
    fun translateOnAxis(axis: Vec3, distance: Float): Node {
        position.addScaledVector(axis.clone().applyQuat(getQuaternion(Quat())), distance)
        return this
    }

    /**
     * three.js `Object3D.lookAt`: orients the node so its +Z axis points at [target] (world space),
     * or its -Z axis for cameras and lights. Accounts for a rotated parent.
     */
    fun lookAt(target: Vec3): Node {
        updateWorldMatrix(true, false)
        val pos = Vec3().setFromMatrixPosition(worldMatrix)
        val m = Mat4()
        if (this is PerspectiveCamera || this is Light) m.lookAt(pos, target, up) else m.lookAt(target, pos, up)
        val q = Quat().setFromRotationMatrix(m)
        val p = parent
        if (p != null) {
            val pr = Quat().setFromRotationMatrix(Mat4().extractRotation(p.worldMatrix))
            q.premultiply(pr.invert())
        }
        return setQuaternion(q)
    }

    fun lookAt(x: Float, y: Float, z: Float): Node = lookAt(Vec3(x, y, z))

    /** World position (updates ancestors' matrices first, like three.js). */
    fun getWorldPosition(target: Vec3 = Vec3()): Vec3 {
        updateWorldMatrix(true, false)
        return target.setFromMatrixPosition(worldMatrix)
    }

    fun getWorldQuaternion(target: Quat = Quat()): Quat {
        updateWorldMatrix(true, false)
        worldMatrix.decompose(Vec3(), target, Vec3())
        return target
    }

    fun getWorldScale(target: Vec3 = Vec3()): Vec3 {
        updateWorldMatrix(true, false)
        worldMatrix.decompose(Vec3(), Quat(), target)
        return target
    }

    /** World-space +Z axis (three.js `getWorldDirection`; cameras override with -Z). */
    open fun getWorldDirection(target: Vec3 = Vec3()): Vec3 {
        updateWorldMatrix(true, false)
        val e = worldMatrix.e
        return target.set(e[8], e[9], e[10]).normalize()
    }

    /** Local point -> world (three.js `localToWorld`); uses the current [worldMatrix]. */
    fun localToWorld(v: Vec3): Vec3 = v.applyMat4(worldMatrix)

    /** World point -> local (three.js `worldToLocal`); uses the current [worldMatrix]. */
    fun worldToLocal(v: Vec3): Vec3 = v.applyMat4(Mat4().invert(worldMatrix))

    private companion object {
        val X_AXIS = Vec3(1f, 0f, 0f)
        val Y_AXIS = Vec3(0f, 1f, 0f)
        val Z_AXIS = Vec3(0f, 0f, 1f)
    }
}
