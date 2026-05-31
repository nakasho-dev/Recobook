package org.ukky.recobook.network

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual fun isDebugNetworkLoggingEnabled(): Boolean {
    return Platform.isDebugBinary
}
