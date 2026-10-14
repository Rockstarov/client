package net.nexus.app.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

/** Parses vless:// vmess:// trojan:// ss:// share links into Xray outbound objects. */
object LinkParser {

    fun parseLines(text: String): List<Server> =
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .mapNotNull { runCatching { parse(it) }.getOrNull() }.toList()

    fun parse(link: String): Server? = when {
        link.startsWith("vless://", true) -> parseVless(link)
        link.startsWith("trojan://", true) -> parseTrojan(link)
        link.startsWith("vmess://", true) -> parseVmess(link)
        link.startsWith("ss://", true) -> parseShadowsocks(link)
        else -> null
    }

    // ---------- helpers ----------

    private fun dec(s: String?): String = if (s == null) "" else URLDecoder.decode(s, "UTF-8")

    private fun queryMap(q: String?): Map<String, String> {
        if (q.isNullOrEmpty()) return emptyMap()
        return q.split("&").filter { it.isNotEmpty() }.associate {
            val i = it.indexOf('=')
            if (i < 0) dec(it) to "" else dec(it.substring(0, i)) to dec(it.substring(i + 1))
        }
    }

    private fun b64(s: String): String {
        val flags = Base64.DEFAULT or Base64.URL_SAFE
        return String(Base64.decode(s.trim().replace("\n", ""), flags), Charsets.UTF_8)
    }

    private fun newId() = UUID.randomUUID().toString()

    /** Builds the Xray streamSettings object shared by vless / vmess / trojan. */
    private fun streamSettings(
        type: String, security: String, q: Map<String, String>, host: String,
    ): JSONObject {
        val net = when (type.lowercase()) {
            "", "tcp", "raw" -> "tcp"
            "splithttp", "xhttp" -> "xhttp"
            "h2", "http" -> "h2"
            else -> type.lowercase()
        }
        val ss = JSONObject().put("network", net).put("security", security)

        when (net) {
            "ws" -> ss.put("wsSettings", JSONObject()
                .put("path", q["path"] ?: "/")
                .put("headers", JSONObject().apply { q["host"]?.takeIf { it.isNotEmpty() }?.let { put("Host", it) } }))
            "grpc" -> ss.put("grpcSettings", JSONObject()
                .put("serviceName", q["serviceName"] ?: q["path"] ?: "")
                .put("multiMode", q["mode"] == "multi"))
            "httpupgrade" -> ss.put("httpupgradeSettings", JSONObject()
                .put("path", q["path"] ?: "/").put("host", q["host"] ?: ""))
            "h2" -> ss.put("httpSettings", JSONObject()
                .put("path", q["path"] ?: "/")
                .put("host", JSONArray().apply { (q["host"] ?: "").split(",").filter { it.isNotEmpty() }.forEach { put(it) } }))
            "xhttp" -> ss.put("xhttpSettings", JSONObject().apply {
                put("path", q["path"] ?: "/")
                q["host"]?.takeIf { it.isNotEmpty() }?.let { put("host", it) }
                put("mode", q["mode"]?.takeIf { it.isNotEmpty() } ?: "auto")
                // "extra" carries a JSON blob with extra xhttp options (padding, xmux, ...)
                q["extra"]?.takeIf { it.isNotEmpty() }?.let { runCatching { put("extra", JSONObject(it)) } }
            })
            "tcp" -> if (q["headerType"] == "http") {
                ss.put("tcpSettings", JSONObject().put("header", JSONObject()
                    .put("type", "http")
                    .put("request", JSONObject()
                        .put("path", JSONArray().put(q["path"] ?: "/"))
                        .put("headers", JSONObject().put("Host", JSONArray().put(q["host"] ?: host))))))
            }
        }

        when (security) {
            "tls" -> ss.put("tlsSettings", JSONObject().apply {
                put("serverName", q["sni"]?.takeIf { it.isNotEmpty() } ?: q["host"]?.takeIf { it.isNotEmpty() } ?: host)
                q["fp"]?.takeIf { it.isNotEmpty() }?.let { put("fingerprint", it) }
                q["alpn"]?.takeIf { it.isNotEmpty() }?.let { a ->
                    put("alpn", JSONArray().apply { a.split(",").forEach { put(it) } })
                }
                if (q["allowInsecure"] == "1" || q["insecure"] == "1") put("allowInsecure", true)
            })
            "reality" -> ss.put("realitySettings", JSONObject().apply {
                put("serverName", q["sni"] ?: host)
                put("fingerprint", q["fp"]?.takeIf { it.isNotEmpty() } ?: "chrome")
                put("publicKey", q["pbk"] ?: "")
                put("shortId", q["sid"] ?: "")
                put("spiderX", q["spx"] ?: "")
                q["pqv"]?.takeIf { it.isNotEmpty() }?.let { put("mldsa65Verify", it) }
            })
        }
        return ss
    }

    private fun secOf(q: Map<String, String>) = when (val s = (q["security"] ?: "none").lowercase()) {
        "", "none" -> "none"
        else -> s
    }

    private fun outbound(protocol: String, settings: JSONObject, stream: JSONObject?) =
        JSONObject().put("tag", "proxy").put("protocol", protocol).put("settings", settings).apply {
            if (stream != null) put("streamSettings", stream)
        }

    private fun wrap(name: String, proto: String, net: String, sec: String, out: JSONObject) = Server(
        id = newId(), name = name.ifBlank { "server" }, protocol = proto, network = net, security = sec,
        source = "LINK", outbounds = JSONArray().put(out).toString(),
    )

    // ---------- protocols ----------

    /** java.net.URI rejects raw spaces in the #fragment, so the name is split off first. */
    private fun uriOf(link: String) = URI(link.substringBefore('#'))
    private fun nameOf(link: String) = dec(link.substringAfter('#', "").replace("+", "%2B"))

    private fun parseVless(link: String): Server {
        val u = uriOf(link)
        val q = queryMap(u.rawQuery)
        val security = secOf(q)
        val host = u.host
        val stream = streamSettings(q["type"] ?: "tcp", security, q, host)
        val user = JSONObject().put("id", u.userInfo).put("encryption", q["encryption"] ?: "none")
            .apply { q["flow"]?.takeIf { it.isNotEmpty() }?.let { put("flow", it) } }
        val settings = JSONObject().put("vnext", JSONArray().put(JSONObject()
            .put("address", host).put("port", u.port).put("users", JSONArray().put(user))))
        return wrap(nameOf(link), "vless", stream.getString("network"), security,
            outbound("vless", settings, stream))
    }

    private fun parseTrojan(link: String): Server {
        val u = uriOf(link)
        val q = queryMap(u.rawQuery)
        val security = if (q["security"].isNullOrEmpty()) "tls" else secOf(q)
        val stream = streamSettings(q["type"] ?: "tcp", security, q, u.host)
        val settings = JSONObject().put("servers", JSONArray().put(JSONObject()
            .put("address", u.host).put("port", u.port).put("password", dec(u.userInfo))))
        return wrap(nameOf(link), "trojan", stream.getString("network"), security,
            outbound("trojan", settings, stream))
    }

    private fun parseVmess(link: String): Server {
        val j = JSONObject(b64(link.substring("vmess://".length)))
        val host = j.getString("add")
        val tls = j.optString("tls")
        val security = if (tls == "tls") "tls" else "none"
        val q = mutableMapOf<String, String>()
        q["path"] = j.optString("path"); q["host"] = j.optString("host")
        q["sni"] = j.optString("sni"); q["fp"] = j.optString("fp"); q["alpn"] = j.optString("alpn")
        if (j.optString("type") == "http") q["headerType"] = "http"
        if (q["path"].isNullOrEmpty()) q.remove("path")
        val stream = streamSettings(j.optString("net", "tcp"), security, q, host)
        val user = JSONObject().put("id", j.getString("id"))
            .put("alterId", j.optString("aid", "0").toIntOrNull() ?: 0)
            .put("security", j.optString("scy").ifEmpty { "auto" })
        val settings = JSONObject().put("vnext", JSONArray().put(JSONObject()
            .put("address", host).put("port", j.get("port").toString().toInt())
            .put("users", JSONArray().put(user))))
        return wrap(j.optString("ps"), "vmess", stream.getString("network"), security,
            outbound("vmess", settings, stream))
    }

    private fun parseShadowsocks(link: String): Server {
        var body = link.substring("ss://".length)
        val name = body.substringAfter('#', "").let { dec(it) }
        body = body.substringBefore('#')
        val query = body.substringAfter('?', "")
        body = body.substringBefore('?').trimEnd('/')

        val method: String; val password: String; val host: String; val port: Int
        if (body.contains('@')) {
            val ui = body.substringBeforeLast('@')
            val hp = body.substringAfterLast('@')
            // userinfo is either base64(method:password) or plain method:password (percent-encoded)
            val cred = if (ui.contains(':')) dec(ui) else b64(ui)
            method = cred.substringBefore(':'); password = cred.substringAfter(':')
            host = hp.substringBeforeLast(':').trim('[', ']'); port = hp.substringAfterLast(':').toInt()
        } else {
            val all = b64(body)
            val ui = all.substringBeforeLast('@'); val hp = all.substringAfterLast('@')
            method = ui.substringBefore(':'); password = ui.substringAfter(':')
            host = hp.substringBeforeLast(':').trim('[', ']'); port = hp.substringAfterLast(':').toInt()
        }

        val q = queryMap(query)
        // SIP003 plugins other than v2ray-plugin are not supported by the Xray core
        val stream = if (q["type"] != null || q["security"] != null)
            streamSettings(q["type"] ?: "tcp", secOf(q), q, host) else null
        val settings = JSONObject().put("servers", JSONArray().put(JSONObject()
            .put("address", host).put("port", port).put("method", method).put("password", password)))
        return wrap(name, "shadowsocks", stream?.getString("network") ?: "tcp", stream?.let { secOf(q) } ?: "none",
            outbound("shadowsocks", settings, stream))
    }
}
