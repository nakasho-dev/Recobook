package org.ukky.recobook.network

internal actual fun isDebugNetworkLoggingEnabled(): Boolean {
    return System.getProperty("recobook.debug") == "true"
}
