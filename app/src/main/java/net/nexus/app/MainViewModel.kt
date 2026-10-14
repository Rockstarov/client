package net.nexus.app

import android.app.Application
import android.net.TrafficStats
import android.os.Process
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import net.nexus.app.core.ConfigBuilder
import net.nexus.app.core.XrayCore
import net.nexus.app.data.FetchResult
import net.nexus.app.data.Provider
import net.nexus.app.data.Server
import net.nexus.app.data.Settings
import net.nexus.app.data.SplitMode
import net.nexus.app.data.Store
import net.nexus.app.data.SubscriptionFetcher
import net.nexus.app.vpn.VpnState
import net.nexus.app.vpn.VpnStatus

/** A message for the UI, resolved to text in the activity so it follows the app language. */
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList())

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    val providers = MutableStateFlow(store.loadProviders())
    val settings = MutableStateFlow(store.loadSettings())
    val events = MutableSharedFlow<UiText>(extraBufferCapacity = 8)

    val refreshing = MutableStateFlow<Set<String>>(emptySet())
    val pinging = MutableStateFlow<Set<String>>(emptySet())
    val adding = MutableStateFlow(false)
    val checkResult = MutableStateFlow<UiText?>(null)
    /** (upload, download) bytes per second of this app's real network traffic while connected. */
    val speed = MutableStateFlow(0L to 0L)

    private val pingGate = Semaphore(4)

    init {
        ensureSelection()
        startSpeedTicker()
        refreshStale()
    }

    // ---------- state helpers ----------

    private fun mutate(f: (List<Provider>) -> List<Provider>) {
        providers.update(f)
        store.saveProviders(providers.value)
    }

    private fun updateSettings(f: (Settings) -> Settings) {
        settings.update(f)
        store.saveSettings(settings.value)
    }

    private fun emit(@StringRes id: Int, vararg args: Any) {
        events.tryEmit(UiText(id, args.toList()))
    }

    fun allServers(): List<Server> = providers.value.flatMap { it.servers }

    private fun ensureSelection() {
        val sel = settings.value.selectedServerId
        if (sel == null || allServers().none { it.id == sel }) {
            updateSettings { it.copy(selectedServerId = allServers().firstOrNull()?.id) }
        }
    }

    // ---------- adding / refreshing ----------

    fun addInput(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            adding.value = true
            try {
                val t = text.trim()
                if (t.startsWith("http://", true) || t.startsWith("https://", true)) {
                    val url = t.lineSequence().first().trim()
                    val r = SubscriptionFetcher.fetch(url)
                    upsertSubscription(url, r)
                    emit(R.string.add_ok, r.servers.size)
                } else {
                    val servers = SubscriptionFetcher.parseBody(t)
                    if (servers.isEmpty()) {
                        emit(R.string.add_fail)
                    } else {
                        addLocal(servers)
                        emit(R.string.add_ok, servers.size)
                    }
                }
            } catch (e: Exception) {
                emit(R.string.refresh_failed, e.message ?: e.javaClass.simpleName)
            } finally {
                adding.value = false
            }
        }
    }

    private fun addLocal(servers: List<Server>) {
        mutate { list ->
            val local = list.firstOrNull { it.isLocal }
                ?: Provider(id = "local", name = getApplication<Application>().getString(R.string.local_provider))
            val updated = local.copy(servers = local.servers + servers, updatedAt = System.currentTimeMillis())
            if (list.any { it.isLocal }) list.map { if (it.isLocal) updated else it } else list + updated
        }
        ensureSelection()
    }

    /** Keeps ids (and therefore the selected server and ping values) stable across refreshes. */
    private fun mergeServers(old: List<Server>, new: List<Server>): List<Server> {
        val byName = old.groupBy { it.name }.mapValues { it.value.toMutableList() }
        return new.map { n ->
            val match = byName[n.name]?.removeFirstOrNull()
            if (match != null) n.copy(id = match.id, pingMs = match.pingMs) else n
        }
    }

    private fun upsertSubscription(url: String, r: FetchResult) {
        mutate { list ->
            val existing = list.firstOrNull { it.url == url }
            val p = (existing ?: Provider(id = java.util.UUID.randomUUID().toString(), name = r.name, url = url)).copy(
                name = r.name,
                servers = mergeServers(existing?.servers ?: emptyList(), r.servers),
                upload = r.upload, download = r.download, total = r.total, expireAt = r.expireAt,
                announce = r.announce, channelUrl = r.channelUrl, supportUrl = r.supportUrl, siteUrl = r.siteUrl,
                updatedAt = System.currentTimeMillis(),
            )
            if (existing != null) list.map { if (it.id == existing.id) p else it } else list + p
        }
        ensureSelection()
    }

    fun refresh(providerId: String, quiet: Boolean = false) {
        val p = providers.value.firstOrNull { it.id == providerId } ?: return
        val url = p.url ?: return
        if (providerId in refreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            refreshing.update { it + providerId }
            try {
                val r = SubscriptionFetcher.fetch(url)
                upsertSubscription(url, r)
                if (!quiet) emit(R.string.refreshed, r.servers.size)
            } catch (e: Exception) {
                if (!quiet) emit(R.string.refresh_failed, e.message ?: e.javaClass.simpleName)
            } finally {
                refreshing.update { it - providerId }
            }
        }
    }

    private fun refreshStale() {
        val limit = 6 * 3600 * 1000L
        providers.value.filter { it.url != null && System.currentTimeMillis() - it.updatedAt > limit }
            .forEach { refresh(it.id, quiet = true) }
    }

    // ---------- editing ----------

    fun select(serverId: String) = updateSettings { it.copy(selectedServerId = serverId) }

    fun toggleExpanded(providerId: String) =
        mutate { l -> l.map { if (it.id == providerId) it.copy(expanded = !it.expanded) else it } }

    fun deleteProvider(providerId: String) {
        mutate { l -> l.filterNot { it.id == providerId } }
        ensureSelection()
    }

    fun deleteServer(serverId: String) {
        mutate { l -> l.map { p -> p.copy(servers = p.servers.filterNot { it.id == serverId }) }
            .filter { !it.isLocal || it.servers.isNotEmpty() } }
        ensureSelection()
    }

    // ---------- latency ----------

    fun pingServers(servers: List<Server>) {
        if (servers.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val ids = servers.map { it.id }.toSet()
            pinging.update { it + ids }
            runCatching { XrayCore.init(getApplication()) }
            val url = settings.value.testUrl
            servers.map { s ->
                async {
                    pingGate.withPermit {
                        val ms = runCatching { XrayCore.measure(ConfigBuilder.forPing(s), url).toInt() }
                            .getOrNull()?.takeIf { it > 0 } ?: -2
                        mutate { l -> l.map { p -> p.copy(servers = p.servers.map { if (it.id == s.id) it.copy(pingMs = ms) else it }) } }
                        pinging.update { it - s.id }
                        s.id to ms
                    }
                }
            }.awaitAll()
            pinging.update { it - ids }
        }
    }

    fun pingProvider(providerId: String) {
        providers.value.firstOrNull { it.id == providerId }?.let { pingServers(it.servers) }
    }

    /** The "Check" link on the main screen: real round-trip through the selected server. */
    fun checkSelected() {
        val s = allServers().firstOrNull { it.id == settings.value.selectedServerId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            checkResult.value = UiText(R.string.checking)
            runCatching { XrayCore.init(getApplication()) }
            val ms = runCatching { XrayCore.measure(ConfigBuilder.forPing(s), settings.value.testUrl).toInt() }.getOrNull()
            mutate { l -> l.map { p -> p.copy(servers = p.servers.map { if (it.id == s.id) it.copy(pingMs = ms?.takeIf { m -> m > 0 } ?: -2) else it }) } }
            checkResult.value = if (ms != null && ms > 0) UiText(R.string.check_result, listOf(ms)) else UiText(R.string.check_fail)
        }
    }

    // ---------- settings ----------

    fun setSplitMode(m: SplitMode) = updateSettings { it.copy(splitMode = m) }
    fun toggleSplitApp(pkg: String) =
        updateSettings { it.copy(splitApps = if (pkg in it.splitApps) it.splitApps - pkg else it.splitApps + pkg) }
    fun setTestUrl(url: String) = updateSettings { it.copy(testUrl = url.trim().ifEmpty { Settings().testUrl }) }

    // ---------- speed ----------

    private fun startSpeedTicker() {
        viewModelScope.launch {
            var lastTx = 0L; var lastRx = 0L; var wasUp = false
            while (true) {
                delay(1000)
                if (VpnState.status.value == VpnStatus.CONNECTED) {
                    val uid = Process.myUid()
                    val tx = TrafficStats.getUidTxBytes(uid).coerceAtLeast(0)
                    val rx = TrafficStats.getUidRxBytes(uid).coerceAtLeast(0)
                    if (wasUp) speed.value = (tx - lastTx).coerceAtLeast(0) to (rx - lastRx).coerceAtLeast(0)
                    lastTx = tx; lastRx = rx; wasUp = true
                } else {
                    wasUp = false
                    if (speed.value != 0L to 0L) speed.value = 0L to 0L
                }
            }
        }
    }
}
