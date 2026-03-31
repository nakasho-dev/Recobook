package org.ukky.recobook.platform

import android.content.Context

object AndroidContextHolder {
    lateinit var appContext: Context
        private set

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
}
