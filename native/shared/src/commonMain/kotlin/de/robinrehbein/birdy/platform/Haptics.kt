package de.robinrehbein.birdy.platform

/**
 * One-shot vibration (`navigator.vibrate(ms)`). Callers apply the mute gate themselves
 * (`buzz()` is a no-op while audio is muted, main-a.md §1.2); implementations swallow errors.
 */
interface Haptics {
    fun vibrate(millis: Int)
}

object NoHaptics : Haptics {
    override fun vibrate(millis: Int) = Unit
}
