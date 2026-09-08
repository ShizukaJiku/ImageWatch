package io.github.shizukajiku.imagewatch

import org.slf4j.LoggerFactory

actual class Log actual constructor(name: String) {
    private val delegate = LoggerFactory.getLogger(name)

    actual fun debug(message: String) = delegate.debug(message)

    actual fun info(message: String) = delegate.info(message)

    actual fun warn(message: String, error: Throwable?) {
        if (error == null) delegate.warn(message) else delegate.warn(message, error)
    }

    actual fun error(message: String, error: Throwable?) {
        if (error == null) delegate.error(message) else delegate.error(message, error)
    }
}
