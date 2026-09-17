package id.waspadai.app.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ApiClient {
    fun create(): HttpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            )
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
            requestTimeoutMillis = 150_000   // contract §4: Android AI timeout = 150 s
            socketTimeoutMillis = 150_000
        }
    }
}
