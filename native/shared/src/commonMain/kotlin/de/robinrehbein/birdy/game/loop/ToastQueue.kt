package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.game.ToastUi

/**
 * main.js `toast()`/`updateToast(dt)` (main-b.md §14): FIFO, one toast visible at a time for
 * 2.2 s, the next one appears on the frame after the previous one hid. Only `resetGame()` clears
 * the queue (a toast queued right before death still shows over the game-over screen).
 */
class ToastQueue {
    private val queue = ArrayDeque<String>()
    private var timer = 0.0
    private var nextId = 0
    private var text: String? = null

    /** Queued (not yet shown) toasts. */
    val pending: List<String> get() = queue.toList()

    fun push(text: String) {
        queue.addLast(text)
    }

    /** `toastQueue.length = 0` (resetGame); a toast already on screen finishes. */
    fun clear() = queue.clear()

    fun update(dt: Double) {
        if (timer > 0) {
            timer -= dt
            if (timer <= 0) text = null
            return
        }
        if (queue.isNotEmpty()) {
            text = queue.removeFirst()
            nextId++
            timer = SHOW_SECONDS
        }
    }

    /** The visible toast, or null. */
    val visible: ToastUi?
        get() = text?.let { ToastUi(nextId, it, (SHOW_SECONDS - timer).toFloat()) }

    companion object {
        const val SHOW_SECONDS = 2.2
    }
}
