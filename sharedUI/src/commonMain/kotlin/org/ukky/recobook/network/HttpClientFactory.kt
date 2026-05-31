package org.ukky.recobook.network

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import co.touchlab.kermit.Logger as KermitLogger

private val networkLogger = KermitLogger.withTag("RecobookHttp")

internal fun createRecobookHttpClient(
    json: Json,
    engine: HttpClientEngine? = null,
    enableNetworkLogging: Boolean = isDebugNetworkLoggingEnabled(),
    logger: Logger = PlatformKtorLogger,
): HttpClient {
    return if (engine != null) {
        HttpClient(engine) {
            configureRecobookHttpClient(
                json = json,
                enableNetworkLogging = enableNetworkLogging,
                logger = logger,
            )
        }
    } else {
        HttpClient {
            configureRecobookHttpClient(
                json = json,
                enableNetworkLogging = enableNetworkLogging,
                logger = logger,
            )
        }
    }
}

internal fun io.ktor.client.HttpClientConfig<*>.configureRecobookHttpClient(
    json: Json,
    enableNetworkLogging: Boolean,
    logger: Logger,
) {
    install(ContentNegotiation) {
        json(json)
    }
    if (enableNetworkLogging) {
        install(Logging) {
            this.logger = logger
            level = LogLevel.HEADERS
        }
    }
}

internal val PlatformKtorLogger = object : Logger {
    override fun log(message: String) {
        networkLogger.d { message }
    }
}

internal expect fun isDebugNetworkLoggingEnabled(): Boolean
