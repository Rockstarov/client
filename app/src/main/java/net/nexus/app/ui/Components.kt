package net.nexus.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.nexus.app.MainViewModel
import net.nexus.app.R
import net.nexus.app.data.Provider
import net.nexus.app.data.Server
import kotlin.math.ceil

// ---------------------------------------------------------------- small pieces

@Composable
fun FlagBox(name: String, size: Dp = 48.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B1D26)),
        contentAlignment = Alignment.Center,
    ) { Text(flagFor(name), fontSize = (size.value * 0.5f).sp) }
}

private fun AnnotatedString.Builder.part(text: String, color: Color) =
    withStyle(SpanStyle(color = color)) { append(text) }

/** "VLESS · XHTTP · REALITY · JSON" with the same colour coding as the reference. */
@Composable
fun TagLine(s: Server) {
    val text = buildAnnotatedString {
        part(protoLabel(s.protocol), if (s.protocol == "shadowsocks") NX.green else NX.accent)
        part(" · ", NX.muted)
        part(s.network.uppercase(), NX.muted2)
        if (s.security != "none") {
            part(" · ", NX.muted); part(s.security.uppercase(), NX.accent)
        }
        part(" · ", NX.muted)
        part(s.source, if (s.source == "JSON") NX.yellow else NX.purple)
    }
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun PingBadge(ms: Int, loading: Boolean) {
    if (loading) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = NX.accent)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            ms == -1 -> Text("—", color = NX.muted, fontFamily = FontFamily.Monospace)
            ms == -2 -> Text("n/a", color = NX.muted, fontFamily = FontFamily.Monospace)
            else -> {
                val c = when { ms < 250 -> NX.green; ms < 500 -> NX.yellow; else -> NX.red }
                Box(Modifier.size(8.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(8.dp))
                Text("${ms}ms", color = NX.muted2, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServerRow(
    server: Server, selected: Boolean, pinging: Boolean,
    onClick: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Box {
        Row(
            Modifier.fillMaxWidth().clip(shape)
                .then(if (selected) Modifier.background(NX.accentDim).border(1.dp, NX.accent.copy(alpha = 0.45f), shape) else Modifier)
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlagBox(server.name)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(server.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TagLine(server)
            }
            Spacer(Modifier.width(8.dp))
            PingBadge(server.pingMs, pinging)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_server)) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

@Composable
fun IconCircleButton(icon: ImageVector, onClick: () -> Unit, spinning: Boolean = false) {
    val angle = if (spinning) {
        val t = rememberInfiniteTransition(label = "spin")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "a").value
    } else 0f
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(NX.iconBtn).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = NX.muted2, modifier = Modifier.size(22.dp).rotate(angle)) }
}

@Composable
private fun LinkChip(icon: ImageVector, label: String, color: Color, url: String) {
    val uri = LocalUriHandler.current
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(NX.chip)
            .clickable { runCatching { uri.openUri(url) } }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- provider card

@Composable
fun ProviderCard(
    p: Provider, selectedId: String?, refreshing: Boolean, pinging: Set<String>,
    onToggle: () -> Unit, onRefresh: () -> Unit, onPingAll: () -> Unit, onDeleteProvider: () -> Unit,
    onSelect: (Server) -> Unit, onDeleteServer: (Server) -> Unit,
) {
    val clip = LocalClipboardManager.current
    val locale = LocalConfiguration.current.locales[0]
    val ru = locale.language == "ru"
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.dp)

    Column(
        Modifier.fillMaxWidth().clip(shape).background(NX.card).border(1.dp, NX.cardBorder, shape).padding(16.dp),
    ) {
        // header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.KeyboardArrowDown, null, tint = NX.muted2,
                    modifier = Modifier.size(28.dp).rotate(if (p.expanded) 0f else -90f),
                )
                Spacer(Modifier.width(8.dp))
                Text(p.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = NX.muted2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (p.updatedAt > 0) {
                val mins = (System.currentTimeMillis() - p.updatedAt) / 60000
                Text(
                    when {
                        mins < 1 -> stringResource(R.string.updated_now)
                        mins < 60 -> stringResource(R.string.updated_min, mins.toInt())
                        else -> stringResource(R.string.updated_hours, (mins / 60).toInt())
                    },
                    color = NX.muted, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp),
                )
            }
            if (!p.isLocal) {
                IconCircleButton(Icons.Filled.Refresh, onRefresh, spinning = refreshing)
                Spacer(Modifier.width(8.dp))
            }
            IconCircleButton(Icons.Filled.FlashOn, onPingAll)
            Spacer(Modifier.width(8.dp))
            Box {
                IconCircleButton(Icons.Filled.MoreHoriz, { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (p.url != null) DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_copy_url)) },
                        onClick = { menu = false; clip.setText(AnnotatedString(p.url)) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_delete), color = NX.red) },
                        onClick = { menu = false; onDeleteProvider() },
                    )
                }
            }
        }

        if (!p.expanded) return@Column

        // traffic / expiry
        if (p.total > 0 || p.expireAt > 0) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val daysText = if (p.expireAt > 0) {
                    val d = ceil((p.expireAt * 1000 - System.currentTimeMillis()) / 86_400_000.0).toInt().coerceAtLeast(0)
                    stringResource(R.string.days_left, d)
                } else stringResource(R.string.unlimited)
                Text(daysText, color = NX.muted2, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                Text("  |  ", color = NX.muted, fontFamily = FontFamily.Monospace)
                val frac = if (p.total > 0) (p.used.toFloat() / p.total).coerceIn(0f, 1f) else 0f
                Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Color(0xFF232634))) {
                    Box(Modifier.fillMaxWidth(frac.coerceAtLeast(if (p.total > 0) 0.03f else 0f)).fillMaxHeight().background(NX.accent))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    if (p.total > 0) stringResource(R.string.traffic_used, fmtBytes(p.used, ru, locale), fmtBytes(p.total, ru, locale))
                    else stringResource(R.string.traffic_used_unlimited, fmtBytes(p.used, ru, locale)),
                    color = NX.muted2, fontFamily = FontFamily.Monospace, fontSize = 16.sp,
                )
            }
        }

        if (p.announce.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(p.announce, color = NX.muted, fontSize = 19.sp)
        }

        // link chips
        if (p.channelUrl.isNotBlank() || p.supportUrl.isNotBlank() || p.siteUrl.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (p.channelUrl.isNotBlank()) LinkChip(Icons.AutoMirrored.Filled.Send, stringResource(R.string.btn_channel), NX.accent, p.channelUrl)
                if (p.supportUrl.isNotBlank()) LinkChip(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.btn_support), NX.purple, p.supportUrl)
                if (p.siteUrl.isNotBlank()) LinkChip(Icons.Filled.Language, stringResource(R.string.btn_site), NX.muted2, p.siteUrl)
            }
        }

        // updated stamp + count
        if (p.updatedAt > 0) {
            Spacer(Modifier.height(14.dp))
            Text(
                "${fmtDate(p.updatedAt, locale)} · ${p.servers.size}",
                color = NX.muted, fontFamily = FontFamily.Monospace, fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }

        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            p.servers.forEach { s ->
                ServerRow(s, selected = s.id == selectedId, pinging = s.id in pinging,
                    onClick = { onSelect(s) }, onDelete = { onDeleteServer(s) })
            }
        }
    }
}

/** ProviderCard wired to the view model; used by both the Connect and Servers screens. */
@Composable
fun BoundProviderCard(vm: MainViewModel, p: Provider, selectedId: String?, refreshing: Set<String>, pinging: Set<String>) {
    ProviderCard(
        p = p, selectedId = selectedId, refreshing = p.id in refreshing, pinging = pinging,
        onToggle = { vm.toggleExpanded(p.id) },
        onRefresh = { vm.refresh(p.id) },
        onPingAll = { vm.pingProvider(p.id) },
        onDeleteProvider = { vm.deleteProvider(p.id) },
        onSelect = { vm.select(it.id) },
        onDeleteServer = { vm.deleteServer(it.id) },
    )
}

// ---------------------------------------------------------------- floating nav bar

@Composable
fun FloatingNavBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(40.dp)
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth()
            .clip(shape).background(Color(0xF2151720)).border(1.dp, NX.cardBorder, shape).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NavItem(Tab.CONNECT, Icons.Filled.Hub, R.string.tab_connect, selected, onSelect)
        NavItem(Tab.SERVERS, Icons.Filled.Dns, R.string.tab_servers, selected, onSelect)
        NavItem(Tab.SETTINGS, Icons.Filled.Settings, R.string.tab_settings, selected, onSelect)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    tab: Tab, icon: ImageVector, label: Int, selected: Tab, onSelect: (Tab) -> Unit,
) {
    val sel = tab == selected
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(32.dp))
            .background(if (sel) NX.accentDim else Color.Transparent)
            .clickable { onSelect(tab) }.padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (sel) NX.text else NX.muted2, modifier = Modifier.size(26.dp))
        Text(
            stringResource(label), fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = if (sel) NX.text else NX.muted2, maxLines = 1,
        )
    }
}
