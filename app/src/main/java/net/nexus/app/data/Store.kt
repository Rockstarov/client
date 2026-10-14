package net.nexus.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Tiny persistence layer: everything lives as JSON in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nexus", Context.MODE_PRIVATE)

    fun loadProviders(): List<Provider> = runCatching {
        val arr = JSONArray(prefs.getString("providers", "[]"))
        (0 until arr.length()).map { Provider.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun saveProviders(list: List<Provider>) {
        val arr = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }
        prefs.edit().putString("providers", arr.toString()).apply()
    }

    fun loadSettings(): Settings = runCatching {
        val o = JSONObject(prefs.getString("settings", "{}"))
        val apps = o.optJSONArray("splitApps") ?: JSONArray()
        Settings(
            splitMode = runCatching { SplitMode.valueOf(o.optString("splitMode", "OFF")) }.getOrDefault(SplitMode.OFF),
            splitApps = (0 until apps.length()).map { apps.getString(it) }.toSet(),
            testUrl = o.optString("testUrl").ifBlank { Settings().testUrl },
            selectedServerId = o.optString("selectedServerId").ifEmpty { null },
        )
    }.getOrDefault(Settings())

    fun saveSettings(s: Settings) {
        val o = JSONObject()
            .put("splitMode", s.splitMode.name)
            .put("splitApps", JSONArray(s.splitApps.toList()))
            .put("testUrl", s.testUrl)
            .put("selectedServerId", s.selectedServerId)
        prefs.edit().putString("settings", o.toString()).apply()
    }

    /** Looks a server up by id across all providers (used by the VPN service). */
    fun findServer(id: String?): Server? =
        if (id == null) null else loadProviders().asSequence().flatMap { it.servers.asSequence() }.firstOrNull { it.id == id }
}
