package de.robinrehbein.birdy.meta

import de.robinrehbein.birdy.platform.KeyValueStore
import de.robinrehbein.birdy.platform.StorageKeys

/**
 * Typed accessors for the standalone settings keys (not part of `birdy-progress`), matching the
 * JS parsing rules exactly (main-a.md §2.1/§12.5, platform.md §4.7).
 */
class Settings(private val storage: KeyValueStore) {
    /** `birdy-muted`: `'1'` = muted, anything else (incl. absent) = unmuted (audio.js). */
    var muted: Boolean
        get() = storage.getString(StorageKeys.MUTED) == "1"
        set(value) { storage.putString(StorageKeys.MUTED, if (value) "1" else "0") }

    /**
     * `birdy-quality`: `clamp(int(value) || 0, 0, 4)` (main-a.md §2.1/§12.5). `Number(x)||0`
     * semantics: missing/blank/non-numeric all fall back to `0`, never throwing.
     */
    var quality: Int
        get() = (storage.getString(StorageKeys.QUALITY)?.trim()?.toDoubleOrNull()?.toInt() ?: 0).coerceIn(0, 4)
        set(value) { storage.putString(StorageKeys.QUALITY, value.coerceIn(0, 4).toString()) }

    /** `birdy-fps`: `'1'` = overlay on, anything else (incl. absent) = off. */
    var fpsOverlay: Boolean
        get() = storage.getString(StorageKeys.FPS) == "1"
        set(value) { storage.putString(StorageKeys.FPS, if (value) "1" else "") }
}
