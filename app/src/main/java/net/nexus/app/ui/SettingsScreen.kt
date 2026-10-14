package net.nexus.app.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.nexus.app.BuildConfig
import net.nexus.app.MainViewModel
import net.nexus.app.R
import net.nexus.app.core.XrayCore
import net.nexus.app.data.SplitMode

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    var picker by remember { mutableStateOf(false) }

    if (picker) {
        AppPicker(selected = s.splitApps, onToggle = vm::toggleSplitApp, onBack = { picker = false })
        return
    }

    val currentLang = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
    val coreVersion = remember { XrayCore.version() }
    var url by remember(s.testUrl) { mutableStateOf(s.testUrl) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text(stringResource(R.string.settings_title), fontSize = 34.sp, fontWeight = FontWeight.Bold) }

        item {
            Section(stringResource(R.string.settings_language)) {
                RadioRow(currentLang.isEmpty(), stringResource(R.string.lang_system)) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                }
                RadioRow(currentLang == "ru", stringResource(R.string.lang_ru)) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru"))
                }
                RadioRow(currentLang == "en", stringResource(R.string.lang_en)) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                }
            }
        }

        item {
            Section(stringResource(R.string.settings_split)) {
                RadioRow(s.splitMode == SplitMode.OFF, stringResource(R.string.split_off)) { vm.setSplitMode(SplitMode.OFF) }
                RadioRow(s.splitMode == SplitMode.BYPASS, stringResource(R.string.split_bypass)) { vm.setSplitMode(SplitMode.BYPASS) }
                RadioRow(s.splitMode == SplitMode.ONLY, stringResource(R.string.split_only)) { vm.setSplitMode(SplitMode.ONLY) }
                if (s.splitMode != SplitMode.OFF) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { picker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NX.accent),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.split_pick_apps, s.splitApps.size)) }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.split_note), color = NX.muted, fontSize = 13.sp)
                }
            }
        }

        item {
            Section(stringResource(R.string.settings_test_url)) {
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.setTestUrl(url) }),
                )
            }
        }

        item {
            Section(stringResource(R.string.settings_about)) {
                Text(
                    stringResource(R.string.about_text, BuildConfig.VERSION_NAME, coreVersion),
                    color = NX.muted2, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(NX.card).border(1.dp, NX.cardBorder, shape).padding(16.dp)) {
        Text(title, color = NX.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun RadioRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 17.sp)
    }
}

// ---------------------------------------------------------------- split tunneling app picker

private data class AppItem(val pkg: String, val label: String, val icon: ImageBitmap?)

private fun loadApps(ctx: android.content.Context, preselected: Set<String>): List<AppItem> {
    val pm = ctx.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .map { it.activityInfo.packageName }.distinct().filter { it != ctx.packageName }
        .mapNotNull { pkg ->
            runCatching {
                AppItem(
                    pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(),
                    runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                )
            }.getOrNull()
        }
        .sortedWith(compareByDescending<AppItem> { it.pkg in preselected }.thenBy { it.label.lowercase() })
}

@Composable
private fun AppPicker(selected: Set<String>, onToggle: (String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(ctx, selected) } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
            Text(stringResource(R.string.settings_split), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.split_search), color = NX.muted) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = NX.accent) }
        } else {
            val filtered = list.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp)) {
                items(filtered, key = { it.pkg }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(app.pkg) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (app.icon != null) Image(app.icon, null, Modifier.size(44.dp))
                        else Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(NX.iconBtn))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontSize = 17.sp, maxLines = 1)
                            Text(app.pkg, fontSize = 12.sp, color = NX.muted, maxLines = 1, fontFamily = FontFamily.Monospace)
                        }
                        Checkbox(checked = app.pkg in selected, onCheckedChange = null)
                    }
                }
            }
        }
    }
}
