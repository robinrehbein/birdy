package de.robinrehbein.birdy.game.loop

/**
 * `setTimeout` replacement on the game thread: tasks run after [delaySeconds] of real time
 * (they keep counting while paused, like browser timers).
 */
class Scheduler {
    private class Task(var left: Double, val block: () -> Unit)

    private val tasks = ArrayList<Task>()

    fun after(delayMs: Int, block: () -> Unit) {
        tasks += Task(delayMs / 1000.0, block)
    }

    fun advance(dt: Double) {
        if (tasks.isEmpty()) return
        val due = ArrayList<Task>()
        val it = tasks.iterator()
        while (it.hasNext()) {
            val t = it.next()
            t.left -= dt
            if (t.left <= 0) {
                due += t
                it.remove()
            }
        }
        due.forEach { it.block() }
    }

    val size: Int get() = tasks.size
}
