package com.adchain.sdk.utils

import android.util.Log

/**
 * Centralized logging utility for Adchain SDK
 * Provides configurable log levels for production and debug environments
 */
object AdchainLogger {
    /**
     * Current log level - defaults to WARNING for production safety
     */
    @JvmStatic
    var logLevel: LogLevel = LogLevel.WARNING

    /**
     * Log an error message with optional throwable
     */
    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (logLevel.value >= LogLevel.ERROR.value) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }

    /**
     * Log a warning message
     */
    @JvmStatic
    fun w(tag: String, message: String) {
        if (logLevel.value >= LogLevel.WARNING.value) {
            Log.w(tag, message)
        }
    }

    /**
     * Log an info message
     */
    @JvmStatic
    fun i(tag: String, message: String) {
        if (logLevel.value >= LogLevel.INFO.value) {
            Log.i(tag, message)
        }
    }

    /**
     * Log a debug message
     */
    @JvmStatic
    fun d(tag: String, message: String) {
        if (logLevel.value >= LogLevel.DEBUG.value) {
            Log.d(tag, message)
        }
    }

    /**
     * Log a verbose message
     */
    @JvmStatic
    fun v(tag: String, message: String) {
        if (logLevel.value >= LogLevel.VERBOSE.value) {
            Log.v(tag, message)
        }
    }
}

/**
 * Log levels for the SDK
 */
enum class LogLevel(val value: Int) {
    /** No logs will be printed */
    NONE(0),
    /** Only errors will be printed */
    ERROR(1),
    /** Errors and warnings will be printed (Default) */
    WARNING(2),
    /** Errors, warnings, and info messages will be printed */
    INFO(3),
    /** All except verbose messages will be printed */
    DEBUG(4),
    /** All messages will be printed */
    VERBOSE(5)
}