package de.robinrehbein.birdy.game.loop

import de.robinrehbein.birdy.meta.Lang
import de.robinrehbein.birdy.meta.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.Volatile

/**
 * [Strings] handed to the UI before storage is ready: delegates to a provisional table and is
 * switched to the real one (reading the migrated `birdy-lang`) when the game boots. [lang] is a
 * stable flow across the switch. `t()` is safe from any thread.
 */
class DeferredStrings(initial: Strings) : Strings {
    @Volatile
    private var delegate: Strings = initial
    private val state = MutableStateFlow(initial.lang.value)
    override val lang: StateFlow<Lang> = state

    override fun t(key: String, params: Map<String, Any?>): String = delegate.t(key, params)

    override fun setLang(lang: Lang) {
        delegate.setLang(lang)
        state.value = delegate.lang.value
    }

    fun replace(strings: Strings) {
        delegate = strings
        state.value = strings.lang.value
    }
}
