package net.nexus.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * One connectable server.
 *
 * Either [outbounds] (JSON array of Xray outbound objects, built from a share link)
 * or [fullConfig] (a complete Xray JSON config from a subscription) is set.
 */
data class Server(
    val id: String,
    val name: String,
    val protocol: String,       // vless | vmess | trojan | shadowsocks | json
    val network: String,        // tcp | xhttp | ws | grpc | ...
    val security: String,       // reality | tls | none
    val source: String,         // LINK | JSON
    val outbounds: String? = null,
    val fullConfig: String? = null,
    val pingMs: Int = -1,       // -1 = not tested, -2 = failed
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("protocol", protocol)
        .put("network", network).put("security", security).put("source", source)
        .put("outbounds", outbounds).put("fullConfig", fullConfig).put("pingMs", pingMs)

    companion object {
        fun fromJson(o: JSONObject) = Server(
            id = o.getString("id"),
            name = o.optString("name"),
            protocol = o.optString("protocol"),
            network = o.optString("network"),
            security = o.optString("security"),
            source = o.optString("source", "LINK"),
            outbounds = o.optString("outbounds").ifEmpty { null },
            fullConfig = o.optString("fullConfig").ifEmpty { null },
            pingMs = o.optInt("pingMs", -1),
        )
    }
}

data class Provider(
    val id: String,
    val name: String,
    val url: String? = null,            // subscription URL; null for the local "My servers" bucket
    val servers: List<Server> = emptyList(),
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,                // 0 = unlimited / unknown
    val expireAt: Long = 0,             // unix seconds, 0 = never / unknown
    val announce: String = "",
    val channelUrl: String = "",
    val supportUrl: String = "",
    val siteUrl: String = "",
    val updatedAt: Long = 0,            // unix millis
    val expanded: Boolean = true,
) {
    val used: Long get() = upload + download
    val isLocal: Boolean get() = url == null

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("url", url)
        .put("servers", JSONArray().also { a -> servers.forEach { a.put(it.toJson()) } })
        .put("upload", upload).put("download", download).put("total", total)
        .put("expireAt", expireAt).put("announce", announce)
        .put("channelUrl", channelUrl).put("supportUrl", supportUrl).put("siteUrl", siteUrl)
        .put("updatedAt", updatedAt).put("expanded", expanded)

    companion object {
        fun fromJson(o: JSONObject): Provider {
            val arr = o.optJSONArray("servers") ?: JSONArray()
            return Provider(
                id = o.getString("id"),
                name = o.optString("name"),
                url = o.optString("url").ifEmpty { null },
                servers = (0 until arr.length()).map { Server.fromJson(arr.getJSONObject(it)) },
                upload = o.optLong("upload"), download = o.optLong("download"),
                total = o.optLong("total"), expireAt = o.optLong("expireAt"),
                announce = o.optString("announce"),
                channelUrl = o.optString("channelUrl"), supportUrl = o.optString("supportUrl"),
                siteUrl = o.optString("siteUrl"),
                updatedAt = o.optLong("updatedAt"), expanded = o.optBoolean("expanded", true),
            )
        }
    }
}

enum class SplitMode { OFF, BYPASS, ONLY }

data class Settings(
    val splitMode: SplitMode = SplitMode.OFF,
    val splitApps: Set<String> = emptySet(),
    val testUrl: String = "https://www.gstatic.com/generate_204",
    val selectedServerId: String? = null,
)
