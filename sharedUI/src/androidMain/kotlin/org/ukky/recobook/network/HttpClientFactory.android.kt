package org.ukky.recobook.network

import android.content.pm.ApplicationInfo
import org.ukky.recobook.platform.AndroidContextHolder

internal actual fun isDebugNetworkLoggingEnabled(): Boolean {
    if (!AndroidContextHolder.isInitialized) return false
    val flags = AndroidContextHolder.appContext.applicationInfo.flags
    return (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
