package org.ukky.recobook.platform

import android.content.Context

object AndroidContextHolder {
    lateinit var appContext: Context
        private set
    val isInitialized: Boolean
        get() = ::appContext.isInitialized

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
}
