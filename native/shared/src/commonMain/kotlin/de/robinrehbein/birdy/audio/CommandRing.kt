package de.robinrehbein.birdy.audio

import kotlin.concurrent.Volatile

/**
 * Lock-free single-producer (game thread) / single-consumer (audio thread) command queue with
 * pre-allocated primitive slots. The volatile tail/head writes publish the slot contents.
 */
class CommandRing(capacity: Int = 1024) {
    private val size = capacity
    private val op = IntArray(capacity)
    private val arg = DoubleArray(capacity)
    private val arg2 = IntArray(capacity)
    @Volatile private var head = 0 // next slot to read (consumer-owned)
    @Volatile private var tail = 0 // next slot to write (producer-owned)
    /** Commands dropped because the ring was full. */
    var dropped = 0
        private set

    /** Producer side. Returns false (and drops the command) when full. */
    fun push(opcode: Int, a: Double = 0.0, b: Int = 0): Boolean {
        val t = tail
        val next = if (t + 1 == size) 0 else t + 1
        if (next == head) { dropped++; return false }
        op[t] = opcode; arg[t] = a; arg2[t] = b
        tail = next
        return true
    }

    /** Consumer side: calls [handler] for every queued command. */
    inline fun drain(handler: (op: Int, a: Double, b: Int) -> Unit) {
        while (true) {
            val h = headIndex()
            if (h == tailIndex()) return
            handler(opAt(h), argAt(h), arg2At(h))
            advance(h)
        }
    }

    @PublishedApi internal fun headIndex() = head
    @PublishedApi internal fun tailIndex() = tail
    @PublishedApi internal fun opAt(i: Int) = op[i]
    @PublishedApi internal fun argAt(i: Int) = arg[i]
    @PublishedApi internal fun arg2At(i: Int) = arg2[i]
    @PublishedApi internal fun advance(h: Int) { head = if (h + 1 == size) 0 else h + 1 }

    val isEmpty: Boolean get() = head == tail
}
