package de.robinrehbein.birdy.meta.migration

import de.robinrehbein.birdy.meta.LocalProgressRepository
import de.robinrehbein.birdy.platform.FakeClock
import de.robinrehbein.birdy.platform.LegacyMigration
import de.robinrehbein.birdy.platform.LegacyReadException
import de.robinrehbein.birdy.platform.MemoryKeyValueStore
import de.robinrehbein.birdy.platform.StorageKeys
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegacyImportTest {
    @Test
    fun noLegacyDataIsANoOpBesidesTheFlag() = runBlocking {
        val storage = MemoryKeyValueStore()
        runMigrationIfNeeded(storage, object : LegacyMigration {
            override suspend fun readLegacyStorage(): Map<String, String> = emptyMap()
        })
        assertTrue(LegacyImport.alreadyMigrated(storage))
        assertNull(storage.getString(StorageKeys.PROGRESS))
    }

    @Test
    fun importsAllSixKeysVerbatim() {
        val storage = MemoryKeyValueStore()
        val legacy = mapOf(
            StorageKeys.PROGRESS to """{"coins":42,"best":5}""",
            StorageKeys.LEGACY_BEST to "77",
            StorageKeys.MUTED to "1",
            StorageKeys.LANG to "de",
            StorageKeys.QUALITY to "2",
            StorageKeys.FPS to "1",
        )
        LegacyImport.apply(storage, legacy)
        for ((k, v) in legacy) assertEquals(v, storage.getString(k))
        assertEquals("1", storage.getString(StorageKeys.MIGRATED))
    }

    @Test
    fun idempotentReapplyingProducesTheSameResult() {
        val storage = MemoryKeyValueStore()
        val legacy = mapOf(StorageKeys.PROGRESS to """{"coins":10}""", StorageKeys.LEGACY_BEST to "3")
        LegacyImport.apply(storage, legacy)
        val firstDump = storage.map.toMap()
        LegacyImport.apply(storage, legacy)
        assertEquals(firstDump, storage.map.toMap())
    }

    @Test
    fun runMigrationIfNeededRunsOnlyOnce() = runBlocking {
        val storage = MemoryKeyValueStore()
        var calls = 0
        val migration = object : LegacyMigration {
            override suspend fun readLegacyStorage(): Map<String, String> {
                calls++
                return mapOf(StorageKeys.PROGRESS to """{"coins":99}""")
            }
        }
        runMigrationIfNeeded(storage, migration)
        runMigrationIfNeeded(storage, migration)
        assertEquals(1, calls)
        assertEquals("""{"coins":99}""", storage.getString(StorageKeys.PROGRESS))
    }

    @Test
    fun failedReadLeavesMigrationPendingAndIsRetriedNextLaunch() = runBlocking {
        val storage = MemoryKeyValueStore()
        var fail = true
        val migration = object : LegacyMigration {
            override suspend fun readLegacyStorage(): Map<String, String> {
                if (fail) throw LegacyReadException("timeout")
                return mapOf(StorageKeys.PROGRESS to """{"coins":123}""", StorageKeys.LEGACY_BEST to "9")
            }
        }
        assertFailsWith<LegacyReadException> { runMigrationIfNeeded(storage, migration) }
        assertFalse(LegacyImport.alreadyMigrated(storage))
        assertNull(storage.getString(StorageKeys.PROGRESS))

        fail = false
        runMigrationIfNeeded(storage, migration)
        assertTrue(LegacyImport.alreadyMigrated(storage))
        assertEquals("""{"coins":123}""", storage.getString(StorageKeys.PROGRESS))
        assertEquals("9", storage.getString(StorageKeys.LEGACY_BEST))
    }

    /** meta.md §1.3:`birdy-best` vs `birdy-progress.best` is reconciled by
     * [LocalProgressRepository.load] on every load, not by the importer — verify the merge
     * actually happens once the imported keys are read by the repository. */
    @Test
    fun bestMaxMergeHappensOnFirstLoadAfterImport() {
        val storage = MemoryKeyValueStore()
        LegacyImport.apply(storage, mapOf(StorageKeys.PROGRESS to """{"coins":0,"best":5}""", StorageKeys.LEGACY_BEST to "40"))
        val repo = LocalProgressRepository(storage, FakeClock())
        assertEquals(40, repo.data.value.best)
    }

    @Test
    fun corruptProgressJsonFallsBackToDefaultsWithoutCrashing() {
        val storage = MemoryKeyValueStore()
        LegacyImport.apply(storage, mapOf(StorageKeys.PROGRESS to "{not valid json"))
        val repo = LocalProgressRepository(storage, FakeClock())
        assertEquals(0, repo.data.value.coins)
        assertEquals("sunny", repo.equipped(de.robinrehbein.birdy.meta.Kind.Skin).id)
    }
}
