package net.nexus.app.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.delay
import net.nexus.app.BuildConfig
import net.nexus.app.MainViewModel
import net.nexus.app.R
import net.nexus.app.vpn.VpnState
import net.nexus.app.vpn.VpnStatus

@Composable
fun ConnectScreen(vm: MainViewModel, onToggle: () -> Unit, onAddServers: () -> Unit) {
    val providers by vm.providers.collectAsState()
    val settings by vm.settings.collectAsState()
    val status by VpnState.status.collectAsState()
    val connectedAt by VpnState.connectedAt.collectAsState()
    val speed by vm.speed.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val pinging by vm.pinging.collectAsState()
    val check by vm.checkResult.collectAsState()

    val locale = LocalConfiguration.current.locales[0]
    val ru = locale.language == "ru"

    val selected = providers.flatMap { it.servers }.firstOrNull { it.id == settings.selectedServerId }
    val provider = providers.firstOrNull { p -> p.servers.any { it.id == settings.selectedServerId } } ?: providers.firstOrNull()

    val elapsed by produceState(0L, status, connectedAt) {
        while (true) {
            value = if (status == VpnStatus.CONNECTED && connectedAt > 0) System.currentTimeMillis() - connectedAt else 0L
            delay(500)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 130.dp),
    ) {
        // header: logo + version + language globe
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("NEXUS", fontSize = 40.sp, fontWeight = FontWeight.Black, color = NX.accent)
                    Spacer(Modifier.width(10.dp))
                    Text("v${BuildConfig.VERSION_NAME}", fontSize = 18.sp, color = NX.muted, modifier = Modifier.padding(bottom = 6.dp), fontFamily = FontFamily.Monospace)
                }
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(NX.iconBtn).clickable {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(if (ru) "en" else "ru"))
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Language, null, tint = NX.muted2, modifier = Modifier.size(30.dp)) }
            }
        }

        // live stats
        item {
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Stat(Icons.Filled.ArrowUpward, NX.accent, fmtSpeed(speed.first, ru, locale))
                Stat(Icons.Filled.AccessTime, NX.muted2, fmtDuration(elapsed))
                Stat(Icons.Filled.ArrowDownward, NX.purple, fmtSpeed(speed.second, ru, locale))
            }
        }

        // big shield button
        item {
            Spacer(Modifier.height(30.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ConnectButton(status, onToggle) }
        }

        // selected server + status
        item {
            Spacer(Modifier.height(26.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (selected != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(flagFor(selected.name), fontSize = 30.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(selected.name, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = NX.accent)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        when (status) {
                            VpnStatus.CONNECTED -> R.string.status_connected
                            VpnStatus.CONNECTING -> R.string.status_connecting
                            VpnStatus.ERROR -> R.string.status_error
                            VpnStatus.DISCONNECTED -> R.string.status_disconnected
                        }
                    ),
                    color = if (status == VpnStatus.ERROR) NX.red else NX.muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                )
            }
        }

        // "Check" link
        item {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.check), color = NX.accent, fontSize = 25.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = selected != null) { vm.checkSelected() },
                )
                check?.let {
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(it.id, *it.args.toTypedArray()), color = NX.muted2, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                }
            }
        }

        // current provider
        item {
            Spacer(Modifier.height(30.dp))
            Text(
                stringResource(R.string.current_provider), color = NX.muted, fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
            if (provider != null) {
                BoundProviderCard(vm, provider, settings.selectedServerId, refreshing, pinging)
            } else {
                EmptyProvider(onAddServers)
            }
        }
    }
}

@Composable
private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 19.sp)
    }
}

@Composable
private fun EmptyProvider(onAdd: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(NX.card).border(1.dp, NX.cardBorder, shape).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.no_provider_title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.no_provider_text), color = NX.muted, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = NX.accent)) {
            Text(stringResource(R.string.add_servers))
        }
    }
}

@Composable
private fun ConnectButton(status: VpnStatus, onClick: () -> Unit) {
    val connected = status == VpnStatus.CONNECTED
    val connecting = status == VpnStatus.CONNECTING
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    val ring by animateColorAsState(if (connected) NX.accent else NX.accent.copy(alpha = 0.32f), label = "ring")

    Box(
        Modifier.size(260.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val outer = size.minDimension / 2
            if (connected) {
                drawCircle(Brush.radialGradient(listOf(NX.accent.copy(alpha = 0.22f), Color.Transparent), radius = outer), radius = outer)
            }
            val r = outer - stroke
            drawCircle(color = ring, radius = r, style = Stroke(stroke))
            if (connecting) {
                rotate(angle) {
                    drawArc(
                        color = NX.accent, startAngle = 0f, sweepAngle = 100f, useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            drawCircle(
                Brush.verticalGradient(listOf(Color(0xFF1B1D26), Color(0xFF14151B))),
                radius = outer - 26.dp.toPx(),
            )
        }
        Icon(
            Icons.Outlined.Shield, null,
            tint = if (connected || connecting) NX.accent else Color(0xFF6D7390),
            modifier = Modifier.size(120.dp),
        )
    }
}
