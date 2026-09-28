package de.robinrehbein.birdy.platform

/**
 * Persistent string key-value storage (the native replacement for WebView `localStorage`).
 * Keys keep the JS names (`birdy-progress`, `birdy-muted`, `birdy-lang`, `birdy-quality`,
 * `birdy-fps`) so migrated data lands 1:1. Implementations: SharedPreferences (Android),
 * NSUserDefaults (iOS, later), [MemoryKeyValueStore] (tests, desktop screenshots).
 *
 * Writes must be durable-on-return or queued in order ([putString] returns false only when the
 * platform reports a failure, mirroring `progress.save()`'s boolean).
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String): Boolean
    fun remove(key: String)
}

/** Storage keys shared with the legacy JS app (docs/native/spec/platform.md §4.7). */
object StorageKeys {
    const val PROGRESS = "birdy-progress"
    const val LEGACY_BEST = "birdy-best"
    const val MUTED = "birdy-muted"
    const val LANG = "birdy-lang"
    const val QUALITY = "birdy-quality"
    const val FPS = "birdy-fps"
    /** Native-only: set once the WebView localStorage import has completed ("1"). */
    const val MIGRATED = "birdy-native-migrated"

    val LEGACY_KEYS = listOf(PROGRESS, LEGACY_BEST, MUTED, LANG, QUALITY, FPS)
}

/** In-memory [KeyValueStore] for tests and headless tools. */
class MemoryKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
    val map = LinkedHashMap(initial)
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String): Boolean { map[key] = value; return true }
    override fun remove(key: String) { map.remove(key) }
}
