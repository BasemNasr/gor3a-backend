package com.bn.gor3a.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Resilient DNS resolver for Gor3a Android.
 * If the device or emulator's local DNS proxy (e.g. 10.0.2.3) fails with UnknownHostException,
 * this automatically falls back to direct verified Anycast IPs for gor3a-backend.vercel.app.
 */
object ResilientGor3aDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val systemAddresses = Dns.SYSTEM.lookup(hostname)
            if (systemAddresses.isNotEmpty()) systemAddresses else fallback(hostname)
        } catch (e: Exception) {
            println("[ResilientGor3aDns] ⚠️ System DNS failed for $hostname (${e.message}), activating Anycast fallback")
            fallback(hostname)
        }
    }

    private fun fallback(hostname: String): List<InetAddress> {
        return if (hostname.equals("gor3a-backend.vercel.app", ignoreCase = true)) {
            listOf(
                InetAddress.getByAddress("gor3a-backend.vercel.app", byteArrayOf(216.toByte(), 198.toByte(), 79.toByte(), 3.toByte())),
                InetAddress.getByAddress("gor3a-backend.vercel.app", byteArrayOf(64.toByte(), 29.toByte(), 17.toByte(), 3.toByte())),
                InetAddress.getByAddress("gor3a-backend.vercel.app", byteArrayOf(76.toByte(), 76.toByte(), 21.toByte(), 21.toByte()))
            )
        } else {
            throw UnknownHostException("Cannot resolve $hostname on emulator network")
        }
    }
}

actual fun createPlatformHttpClient(): HttpClient {
    return HttpClient(OkHttp) {
        engine {
            config {
                dns(ResilientGor3aDns)
                retryOnConnectionFailure(true)
            }
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                    encodeDefaults = true
                }
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000
            connectTimeoutMillis = 25_000
            socketTimeoutMillis = 90_000
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }
}
