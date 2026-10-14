package net.nexus.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.nexus.app.MainActivity
import net.nexus.app.R
import net.nexus.app.core.ConfigBuilder
import net.nexus.app.core.XrayCore
import net.nexus.app.data.SplitMode
import net.nexus.app.data.Store

class NexusVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn()
            else -> {
                val serverId = intent?.getStringExtra(EXTRA_SERVER_ID)
                showNotification(getString(R.string.notif_connecting))
                scope.launch { startVpn(serverId) }
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn(serverId: String?) {
        val store = Store(this)
        val server = store.findServer(serverId)
        if (server == null) {
            fail("server not found"); return
        }
        VpnState.connecting(server.id)
        var goFd = -1
        try {
            XrayCore.init(this)
            val config = ConfigBuilder.forTunnel(server)

            val pfd = buildTun(store) ?: error("VPN permission was revoked")
            tun = pfd
            // The core gets its own dup'd descriptor and owns/closes it; we keep ours to tear the interface down.
            goFd = pfd.dup().detachFd()

            XrayCore.start(config, goFd) { /* core log line; ignored in release */ }
            goFd = -1 // ownership handed to the core

            VpnState.connected()
            showNotification(getString(R.string.notif_connected, server.name))
        } catch (t: Throwable) {
            if (goFd >= 0) runCatching { ParcelFileDescriptor.adoptFd(goFd).close() }
            XrayCore.stopQuietly()
            runCatching { tun?.close() }; tun = null
            fail(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun buildTun(store: Store): ParcelFileDescriptor? {
        val settings = store.loadSettings()
        val b = Builder()
            .setSession("nexus.net")
            .setMtu(1500)
            .addAddress("172.19.0.1", 30)
            .addRoute("0.0.0.0", 0)
            .addAddress("fdfe:dcba:9876::1", 126)
            .addRoute("::", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
        if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)

        // The app itself always stays outside the tunnel: the core's own sockets to the proxy server
        // must not loop back into the TUN, and subscription updates must work while connected.
        when (settings.splitMode) {
            SplitMode.ONLY -> {
                val apps = settings.splitApps - packageName
                if (apps.isEmpty()) error("Split tunneling: no apps selected")
                apps.forEach { runCatching { b.addAllowedApplication(it) } }
            }
            SplitMode.BYPASS -> {
                settings.splitApps.forEach { runCatching { b.addDisallowedApplication(it) } }
                b.addDisallowedApplication(packageName)
            }
            SplitMode.OFF -> b.addDisallowedApplication(packageName)
        }
        return b.establish()
    }

    private fun fail(message: String) {
        VpnState.failed(message)
        stopForegroundCompat()
        stopSelf()
    }

    private fun stopVpn() {
        scope.launch {
            XrayCore.stopQuietly()
            runCatching { tun?.close() }; tun = null
            VpnState.disconnected()
            stopForegroundCompat()
            stopSelf()
        }
    }

    override fun onRevoke() {
        // user turned the VPN off from system settings / another VPN took over
        stopVpn()
    }

    override fun onDestroy() {
        XrayCore.stopQuietly()
        runCatching { tun?.close() }; tun = null
        if (VpnState.status.value != VpnStatus.ERROR) VpnState.disconnected()
        scope.cancel()
        super.onDestroy()
    }

    // ---------- notification ----------

    private fun stopForegroundCompat() =
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)

    private fun showNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, NexusVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_disconnect), stop)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        const val ACTION_START = "net.nexus.app.START"
        const val ACTION_STOP = "net.nexus.app.STOP"
        const val EXTRA_SERVER_ID = "server_id"
        private const val CHANNEL = "vpn_status"
        private const val NOTIF_ID = 1
    }
}
