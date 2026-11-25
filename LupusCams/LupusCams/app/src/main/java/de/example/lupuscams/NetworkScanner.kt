package de.example.lupuscams

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/** Ein gefundenes Gerät mit offenem RTSP-Port. */
data class FoundDevice(val ip: String, val http: Boolean)

object NetworkScanner {

    /** Erste drei Blöcke der eigenen IPv4-Adresse, z. B. "192.168.178", oder null. */
    fun localPrefix(context: Context): String? {
        return try {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val net = cm.activeNetwork ?: return null
            val props = cm.getLinkProperties(net) ?: return null
            val addr = props.linkAddresses
                .map { it.address }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress } ?: return null
            val host = addr.hostAddress ?: return null
            host.split(".").take(3).joinToString(".")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Prüft alle 254 Adressen des Bereichs auf offenen RTSP-Port 554.
     * onProgress wird mit der Zahl der bereits geprüften Adressen aufgerufen,
     * onFound sofort bei jedem Treffer.
     */
    suspend fun scan(
        prefix: String,
        onProgress: (Int) -> Unit,
        onFound: (FoundDevice) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val done = AtomicInteger(0)
        val limit = Semaphore(64)
        coroutineScope {
            (1..254).map { i ->
                async {
                    limit.withPermit {
                        val ip = "$prefix.$i"
                        if (isOpen(ip, 554, 700)) {
                            onFound(FoundDevice(ip, http = isOpen(ip, 80, 500)))
                        }
                        onProgress(done.incrementAndGet())
                    }
                }
            }.awaitAll()
        }
    }

    private fun isOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
