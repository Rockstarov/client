package net.nexus.app.core

import net.nexus.app.data.Server
import org.json.JSONArray
import org.json.JSONObject

/** Turns a [Server] into the JSON config the Xray core runs. */
object ConfigBuilder {

    private fun tunInbound() = JSONObject()
        .put("tag", "tun-in")
        .put("protocol", "tun")
        .put("settings", JSONObject().put("name", "xray0").put("MTU", 1500).put("userLevel", 0))
        .put("sniffing", JSONObject()
            .put("enabled", true)
            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
            .put("routeOnly", true))

    private fun hasTag(outs: JSONArray, tag: String) =
        (0 until outs.length()).any { outs.getJSONObject(it).optString("tag") == tag }

    private fun ensureBuiltins(outs: JSONArray) {
        if (!hasTag(outs, "direct"))
            outs.put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        if (!hasTag(outs, "block"))
            outs.put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
    }

    /** Full config used to run the VPN (with the TUN inbound). */
    fun forTunnel(server: Server): String {
        server.fullConfig?.let { raw ->
            // Server-supplied config: keep its outbounds / routing / dns, but we own the inbound.
            val cfg = JSONObject(raw)
            cfg.put("inbounds", JSONArray().put(tunInbound()))
            cfg.put("log", JSONObject().put("loglevel", "warning"))
            val outs = cfg.optJSONArray("outbounds") ?: JSONArray().also { cfg.put("outbounds", it) }
            ensureBuiltins(outs)
            return cfg.toString()
        }

        val outs = JSONArray(server.outbounds ?: error("server has no outbounds"))
        ensureBuiltins(outs)
        val cfg = JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("dns", JSONObject()
                .put("servers", JSONArray().put("1.1.1.1").put("8.8.8.8"))
                .put("queryStrategy", "UseIP"))
            .put("inbounds", JSONArray().put(tunInbound()))
            .put("outbounds", outs)
            .put("routing", JSONObject()
                .put("domainStrategy", "AsIs")
                .put("rules", JSONArray().put(JSONObject()
                    .put("type", "field")
                    .put("ip", JSONArray().put("geoip:private"))
                    .put("outboundTag", "direct"))))
        return cfg.toString()
    }

    /** Minimal config (no inbound) used only for the latency test. */
    fun forPing(server: Server): String {
        server.fullConfig?.let { raw ->
            val cfg = JSONObject(raw)
            cfg.remove("inbounds")
            val outs = cfg.optJSONArray("outbounds") ?: JSONArray().also { cfg.put("outbounds", it) }
            ensureBuiltins(outs)
            return cfg.toString()
        }
        val outs = JSONArray(server.outbounds ?: error("server has no outbounds"))
        ensureBuiltins(outs)
        return JSONObject().put("outbounds", outs).toString()
    }
}
