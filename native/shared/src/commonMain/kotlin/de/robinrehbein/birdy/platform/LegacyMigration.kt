package de.robinrehbein.birdy.platform

/**
 * One-time import of the Capacitor WebView `localStorage` (origin `https://localhost`) into
 * [KeyValueStore] (platform.md §4.7). Android implementation loads a hidden WebView on that
 * origin and reads [StorageKeys.LEGACY_KEYS]; other platforms have nothing to migrate.
 */
interface LegacyMigration {
    /**
     * Reads the legacy keys. Returns an empty map only when the read succeeded and there is no
     * legacy data (the normal case for fresh installs). Throws [LegacyReadException] (or any other
     * exception) when the read could not be completed, e.g. timeout or WebView failure, so the
     * migration is retried on the next launch instead of being marked done with nothing imported.
     */
    suspend fun readLegacyStorage(): Map<String, String>
}

/** The legacy storage could not be read this time; the migration must be retried later. */
class LegacyReadException(message: String, cause: Throwable? = null) : Exception(message, cause)

object NoLegacyMigration : LegacyMigration {
    override suspend fun readLegacyStorage(): Map<String, String> = emptyMap()
}
