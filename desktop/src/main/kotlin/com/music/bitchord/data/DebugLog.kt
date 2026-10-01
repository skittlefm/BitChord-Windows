package com.music.bitchord.data

import java.util.logging.Level
import java.util.logging.Logger

object DebugLog {
    fun d(tag: String, message: String) {
        Logger.getLogger(tag).fine(message)
    }

    fun i(tag: String, message: String) {
        Logger.getLogger(tag).info(message)
    }

    fun w(tag: String, message: String) {
        Logger.getLogger(tag).warning(message)
    }

    fun w(tag: String, message: String, error: Throwable) {
        Logger.getLogger(tag).log(Level.WARNING, message, error)
    }

    fun e(tag: String, message: String) {
        Logger.getLogger(tag).severe(message)
    }

    fun e(tag: String, message: String, error: Throwable) {
        Logger.getLogger(tag).log(Level.SEVERE, message, error)
    }
}