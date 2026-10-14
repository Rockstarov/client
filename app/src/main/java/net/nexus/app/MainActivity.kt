package net.nexus.app

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import net.nexus.app.ui.*
import net.nexus.app.vpn.NexusVpnService
import net.nexus.app.vpn.VpnState
import net.nexus.app.vpn.VpnStatus

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexusTheme {
                val ctx = LocalContext.current
                var tab by rememberSaveable { mutableStateOf(Tab.CONNECT) }
                val status by VpnState.status.collectAsState()
                val vpnError by VpnState.error.collectAsState()
                val settings by vm.settings.collectAsState()

                fun startVpn() {
                    val id = settings.selectedServerId
                    if (id == null) {
                        Toast.makeText(ctx, ctx.getString(R.string.no_server_selected), Toast.LENGTH_SHORT).show()
                        return
                    }
                    ContextCompat.startForegroundService(
                        ctx,
                        Intent(ctx, NexusVpnService::class.java)
                            .setAction(NexusVpnService.ACTION_START)
                            .putExtra(NexusVpnService.EXTRA_SERVER_ID, id)
                    )
                }

                val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    if (it.resultCode == RESULT_OK) startVpn()
                    else Toast.makeText(ctx, ctx.getString(R.string.vpn_permission_denied), Toast.LENGTH_SHORT).show()
                }
                val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                LaunchedEffect(Unit) {
                    vm.events.collect { Toast.makeText(ctx, ctx.getString(it.id, *it.args.toTypedArray()), Toast.LENGTH_SHORT).show() }
                }
                LaunchedEffect(vpnError) {
                    vpnError?.let { Toast.makeText(ctx, "${ctx.getString(R.string.status_error)}: $it", Toast.LENGTH_LONG).show() }
                }

                fun toggleVpn() {
                    when (status) {
                        VpnStatus.CONNECTED, VpnStatus.CONNECTING ->
                            ctx.startService(Intent(ctx, NexusVpnService::class.java).setAction(NexusVpnService.ACTION_STOP))
                        else -> {
                            val prepare = VpnService.prepare(ctx)
                            if (prepare != null) vpnPermission.launch(prepare) else startVpn()
                        }
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        Tab.CONNECT -> ConnectScreen(vm, onToggle = ::toggleVpn, onAddServers = { tab = Tab.SERVERS })
                        Tab.SERVERS -> ServersScreen(vm)
                        Tab.SETTINGS -> SettingsScreen(vm)
                    }
                    FloatingNavBar(
                        selected = tab, onSelect = { tab = it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}
