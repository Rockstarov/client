package net.nexus.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val countryNames = mapOf(
    "FINLAND" to "FI", "GERMANY" to "DE", "SWITZERLAND" to "CH", "NETHERLANDS" to "NL", "RUSSIA" to "RU",
    "SWEDEN" to "SE", "FRANCE" to "FR", "POLAND" to "PL", "TURKEY" to "TR", "KAZAKHSTAN" to "KZ",
    "USA" to "US", "UNITED" to "US", "UK" to "GB", "BRITAIN" to "GB", "JAPAN" to "JP", "LATVIA" to "LV",
    "ESTONIA" to "EE", "LITHUANIA" to "LT", "ARMENIA" to "AM", "GEORGIA" to "GE", "SINGAPORE" to "SG",
)

private fun codeToFlag(code: String): String =
    code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

/** Best-effort flag for a server name: an emoji already in the name, "AUTO", an ISO code ("FI", "CH v2") or a country name. */
fun flagFor(name: String): String {
    // flag emoji already present (two regional indicator symbols)
    val cps = name.codePoints().toArray()
    for (i in 0 until cps.size - 1) {
        if (cps[i] in 0x1F1E6..0x1F1FF && cps[i + 1] in 0x1F1E6..0x1F1FF) {
            return String(Character.toChars(cps[i])) + String(Character.toChars(cps[i + 1]))
        }
    }
    val tokens = Regex("[A-Za-z]{2,}").findAll(name).map { it.value.uppercase() }.toList()
    if (tokens.any { it == "AUTO" }) return "🇺🇳" // UN flag, like the reference UI
    val iso = Locale.getISOCountries().toSet()
    tokens.firstOrNull { it.length == 2 && it in iso }?.let { return codeToFlag(it) }
    tokens.firstNotNullOfOrNull { countryNames[it] }?.let { return codeToFlag(it) }
    return "🌐" // globe
}

fun fmtBytes(bytes: Long, ru: Boolean, locale: Locale): String {
    val units = if (ru) listOf("Б", "КБ", "МБ", "ГБ", "ТБ") else listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble(); var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    val digits = if (i >= 3) 2 else if (i >= 1) 1 else 0
    return String.format(locale, "%.${digits}f %s", v, units[i])
}

fun fmtSpeed(bytesPerSec: Long, ru: Boolean, locale: Locale): String {
    val mb = bytesPerSec / 1048576.0
    return if (mb >= 1) String.format(locale, "%.1f %s/s", mb, if (ru) "МБ" else "MB")
    else String.format(locale, "%.0f %s/s", bytesPerSec / 1024.0, if (ru) "КБ" else "KB")
}

fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

fun fmtDate(millis: Long, locale: Locale): String =
    SimpleDateFormat("dd.MM.yy, HH:mm", locale).format(Date(millis))

fun protoLabel(p: String): String = when (p) {
    "vless" -> "VLESS"
    "vmess" -> "VMESS"
    "trojan" -> "TROJAN"
    "shadowsocks" -> "Shadowsocks"
    else -> p.uppercase()
}
