package net.nexus.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

enum class Tab { CONNECT, SERVERS, SETTINGS }

/** Colour tokens sampled from the reference screenshot. */
object NX {
    val bg = Color(0xFF0D0E14)
    val card = Color(0xFF12141C)
    val cardBorder = Color(0xFF2A2D3A)
    val chip = Color(0xFF1B2038)
    val iconBtn = Color(0xFF1C1F2A)
    val accent = Color(0xFF5B8DEF)
    val accentDim = Color(0xFF1A2340)
    val text = Color(0xFFE8EAF2)
    val muted = Color(0xFF8A8FA3)
    val muted2 = Color(0xFFA3A8BC)
    val yellow = Color(0xFFE5C14A)
    val green = Color(0xFF4CC38A)
    val red = Color(0xFFEF5350)
    val purple = Color(0xFF8B7CF6)
}

@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NX.accent,
            onPrimary = Color.White,
            background = NX.bg,
            onBackground = NX.text,
            surface = NX.card,
            onSurface = NX.text,
            surfaceVariant = NX.iconBtn,
            onSurfaceVariant = NX.muted,
            outline = NX.cardBorder,
        )
    ) {
        Surface(color = NX.bg, modifier = Modifier.fillMaxSize()) { content() }
    }
}
