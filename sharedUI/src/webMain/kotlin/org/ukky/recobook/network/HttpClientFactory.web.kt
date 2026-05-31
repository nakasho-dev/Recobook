package org.ukky.recobook.network

import kotlinx.browser.window

internal actual fun isDebugNetworkLoggingEnabled(): Boolean {
    return window.location.hostname in setOf("localhost", "127.0.0.1")
}
