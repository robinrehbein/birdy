package de.robinrehbein.birdy.meta.migration

import de.robinrehbein.birdy.platform.KeyValueStore
import de.robinrehbein.birdy.platform.LegacyMigration
import de.robinrehbein.birdy.platform.StorageKeys

/**
 * One-time import of the Capacitor WebView `localStorage` snapshot into [KeyValueStore]
 * (platform.md §4.7). Pure, testable: takes the already-read legacy key/value map and applies it.
 *
 * Every legacy key is assigned into the same-named native key **verbatim** — `birdy-progress`'s
 * JSON is not merged field-by-field with anything already in [storage] (there is nothing to merge
 * against on a normal migration, since this runs before the game ever reads storage — only after a
 * failed read, which leaves the migrated flag unset, can a fallback session have written native
 * progress, and the legacy snapshot then deliberately wins on the retry — and
 * `LocalProgressRepository.load()` already re-applies the `birdy-best` vs `birdy-progress.best`
 * `max()` merge itself on every load — see meta.md §1.3 step 9 — so simply placing the raw
 * `birdy-best` value under [StorageKeys.LEGACY_BEST] is sufficient for that merge to happen
 * automatically the next time progress is loaded). Assign-not-add makes this idempotent: running
 * it twice with the same input reproduces the same stored values.
 */
object LegacyImport {
    /** Applies [legacy] (as returned by [LegacyMigration.readLegacyStorage]) into [storage] and
     * marks migration complete. Keys absent from [legacy] are left untouched (a fresh install has
     * an empty map, so this is a no-op besides setting the migrated flag). */
    fun apply(storage: KeyValueStore, legacy: Map<String, String>) {
        for (key in StorageKeys.LEGACY_KEYS) {
            legacy[key]?.let { storage.putString(key, it) }
        }
        storage.putString(StorageKeys.MIGRATED, "1")
    }

    fun alreadyMigrated(storage: KeyValueStore): Boolean = storage.getString(StorageKeys.MIGRATED) == "1"
}

/**
 * Entry point: runs the one-time migration if it hasn't happened yet. Safe to call on every app
 * start (idempotent) and safe to call again after an interrupted attempt (re-reading and
 * re-applying the same source data is harmless, see [LegacyImport]).
 *
 * A failed read ([LegacyMigration.readLegacyStorage] throws, e.g.
 * [de.robinrehbein.birdy.platform.LegacyReadException]) propagates to the caller and leaves
 * [StorageKeys.MIGRATED] unset, so the next launch tries again rather than losing the legacy data.
 */
suspend fun runMigrationIfNeeded(storage: KeyValueStore, migration: LegacyMigration) {
    if (LegacyImport.alreadyMigrated(storage)) return
    val legacy = migration.readLegacyStorage()
    LegacyImport.apply(storage, legacy)
}
