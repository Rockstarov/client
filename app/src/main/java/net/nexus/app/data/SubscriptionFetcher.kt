package net.nexus.app.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class FetchResult(
    val name: String,
    val servers: List<Server>,
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expireAt: Long = 0,
    val announce: String = "",
    val channelUrl: String = "",
    val supportUrl: String = "",
    val siteUrl: String = "",
)

/** Downloads a subscription URL and turns it into servers + account info (traffic, expiry, links). */
object SubscriptionFetcher {

    private const val MAX_BYTES = 8 * 1024 * 1024

    fun fetch(url: String): FetchResult {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "nexus.net/1.0 (Xray)")
            setRequestProperty("Accept", "*/*")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")

            val body = conn.inputStream.use { it.readBytes() }.also { if (it.size > MAX_BYTES) error("response too large") }.toString(Charsets.UTF_8)
            fun h(name: String): String = decodeHeader(conn.getHeaderField(name))

            val info = parseUserInfo(conn.getHeaderField("subscription-userinfo"))
            val title = h("profile-title").ifBlank {
                URL(url).let { it.ref?.takeIf { r -> r.isNotBlank() } ?: it.host }
            }

            val servers = parseBody(body)
            if (servers.isEmpty()) error("no servers found")

            return FetchResult(
                name = title,
                servers = servers,
                upload = info["upload"] ?: 0, download = info["download"] ?: 0,
                total = info["total"] ?: 0, expireAt = info["expire"] ?: 0,
                announce = h("announce"),
                channelUrl = h("announce-url"),
                supportUrl = h("support-url"),
                siteUrl = h("profile-web-page-url"),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Header values may be sent as "base64:<data>". */
    private fun decodeHeader(v: String?): String {
        if (v == null) return ""
        if (v.startsWith("base64:", true)) {
            return runCatching { String(Base64.decode(v.substring(7), Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault("")
        }
        return v.trim()
    }

    private fun parseUserInfo(v: String?): Map<String, Long> {
        if (v.isNullOrBlank()) return emptyMap()
        return v.split(";").mapNotNull {
            val kv = it.trim().split("=", limit = 2)
            if (kv.size == 2) kv[0].trim().lowercase() to (kv[1].trim().toDoubleOrNull()?.toLong() ?: return@mapNotNull null)
            else null
        }.toMap()
    }

    /** Accepts: Xray JSON (array or single config), plain share-link lines, or base64 of those lines. */
    fun parseBody(raw: String): List<Server> {
        val text = raw.trim().removePrefix("﻿")
        if (text.startsWith("[") || text.startsWith("{")) {
            parseJsonConfigs(text).takeIf { it.isNotEmpty() }?.let { return it }
        }
        LinkParser.parseLines(text).takeIf { it.isNotEmpty() }?.let { return it }
        val decoded = runCatching {
            String(Base64.decode(text.replace("\n", "").replace("\r", ""), Base64.DEFAULT or Base64.URL_SAFE), Charsets.UTF_8)
        }.getOrNull() ?: return emptyList()
        return LinkParser.parseLines(decoded)
    }

    private val proxyProtocols = setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria", "wireguard", "socks", "http")

    fun parseJsonConfigs(text: String): List<Server> {
        val configs = runCatching {
            if (text.startsWith("[")) JSONArray(text).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            else listOf(JSONObject(text))
        }.getOrNull() ?: return emptyList()

        return configs.mapIndexedNotNull { i, cfg ->
            val outs = cfg.optJSONArray("outbounds") ?: return@mapIndexedNotNull null
            val first = (0 until outs.length()).map { outs.getJSONObject(it) }
                .firstOrNull { it.optString("tag") == "proxy" || it.optString("protocol") in proxyProtocols }
                ?: return@mapIndexedNotNull null
            val stream = first.optJSONObject("streamSettings")
            Server(
                id = UUID.randomUUID().toString(),
                name = cfg.optString("remarks").ifBlank { "config ${i + 1}" },
                protocol = first.optString("protocol"),
                network = stream?.optString("network")?.ifEmpty { "tcp" } ?: "tcp",
                security = stream?.optString("security")?.ifEmpty { "none" } ?: "none",
                source = "JSON",
                fullConfig = cfg.toString(),
            )
        }
    }
}
