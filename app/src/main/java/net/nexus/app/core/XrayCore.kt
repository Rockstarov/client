package net.nexus.app.core

import android.content.Context
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File

/**
 * Thin wrapper over the gomobile Xray core (libv2ray.aar).
 * API used (verified against the AAR): initCoreEnv, newCoreController, startLoop(config, tunFd),
 * stopLoop, measureOutboundDelay, checkVersionX.
 */
object XrayCore {
    private var envReady = false
    private var controller: CoreController? = null

    @Synchronized
    fun init(context: Context) {
        if (envReady) return
        val dir = context.filesDir
        // geoip.dat / geosite.dat ship inside the AAR's assets; the core reads them from a real folder.
        for (name in listOf("geoip.dat", "geosite.dat")) {
            val out = File(dir, name)
            if (!out.exists()) {
                runCatching {
                    context.assets.open(name).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                }
            }
        }
        Libv2ray.initCoreEnv(dir.absolutePath, "")
        envReady = true
    }

    val isRunning: Boolean get() = controller?.isRunning == true

    fun version(): String = runCatching { Libv2ray.checkVersionX() }.getOrDefault("?")

    /** Starts the core on an already-established TUN descriptor. Throws if the config is rejected. */
    @Synchronized
    fun start(config: String, tunFd: Int, onStatus: (String) -> Unit) {
        stopQuietly()
        val handler = object : CoreCallbackHandler {
            override fun startup(): Long = 0
            override fun shutdown(): Long = 0
            override fun onEmitStatus(code: Long, message: String?): Long {
                if (!message.isNullOrBlank()) onStatus(message)
                return 0
            }
        }
        val c = Libv2ray.newCoreController(handler)
        controller = c
        c.startLoop(config, tunFd)
    }

    @Synchronized
    fun stopQuietly() {
        val c = controller ?: return
        controller = null
        runCatching { c.stopLoop() }
    }

    /** Real TCP/HTTP round-trip through the server's outbound, in ms. Throws on failure. */
    fun measure(config: String, url: String): Long = Libv2ray.measureOutboundDelay(config, url)
}
