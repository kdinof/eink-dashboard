package com.eink.dashboard.modules.todoist

import com.eink.dashboard.modules.todoist.security.TokenStore

/** In-memory [TokenStore] for tests — the real one is Keystore-backed and on-device. */
class FakeTokenStore(initial: String? = null) : TokenStore {
    var stored: String? = initial
        private set

    override fun save(token: String) {
        stored = if (token.isBlank()) null else token
    }

    override fun load(): String? = stored

    override fun clear() {
        stored = null
    }
}
