package net.nexus.app.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class VpnStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/** Process-wide VPN state; written by [NexusVpnService], observed by the UI. */
object VpnState {
    private val _status = MutableStateFlow(VpnStatus.DISCONNECTED)
    val status: StateFlow<VpnStatus> = _status

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _connectedAt = MutableStateFlow(0L)
    val connectedAt: StateFlow<Long> = _connectedAt

    private val _serverId = MutableStateFlow<String?>(null)
    val serverId: StateFlow<String?> = _serverId

    fun connecting(serverId: String) {
        _serverId.value = serverId; _error.value = null; _status.value = VpnStatus.CONNECTING
    }

    fun connected() {
        _connectedAt.value = System.currentTimeMillis(); _status.value = VpnStatus.CONNECTED
    }

    fun disconnected() {
        _connectedAt.value = 0; _serverId.value = null; _status.value = VpnStatus.DISCONNECTED
    }

    fun failed(message: String) {
        _connectedAt.value = 0; _serverId.value = null; _error.value = message; _status.value = VpnStatus.ERROR
    }
}
