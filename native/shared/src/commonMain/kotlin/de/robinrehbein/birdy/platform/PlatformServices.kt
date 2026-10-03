package de.robinrehbein.birdy.platform

import de.robinrehbein.birdy.audio.AudioOut

/** Everything platform-specific the shared game needs, injected by the app shell. */
class PlatformServices(
    val storage: KeyValueStore,
    val clock: Clock,
    val haptics: Haptics,
    val audioOut: AudioOut,
    val ads: Ads?,
    val billing: Billing?,
    val migration: LegacyMigration = NoLegacyMigration,
    /** Device language tag, e.g. "de-DE" (navigator.language equivalent). */
    val deviceLanguage: String,
    /** Local reminder notifications; no-op where unsupported. */
    val reminders: Reminders = NoReminders,
)
