package de.example.lupuscams

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Schwenken/Neigen für Foscam-Kameras (z. B. FI9821W/P) über die CGI-Schnittstelle:
 * http://IP:PORT/cgi-bin/CGIProxy.fcgi?cmd=ptzMoveUp&usr=...&pwd=...
 * Als Port wird das Feld "Steuer-Port" der Kamera verwendet (die Weboberfläche, meist 80).
 */
class FoscamPtz(private val cam: Camera) : PtzController {

    private val lock = Mutex()

    override suspend fun move(pan: Float, tilt: Float): String? {
        val cmd = when {
            pan < 0f -> "ptzMoveLeft"
            pan > 0f -> "ptzMoveRight"
            tilt > 0f -> "ptzMoveUp"
            tilt < 0f -> "ptzMoveDown"
            else -> return stop()
        }
        return send(cmd)
    }

    override suspend fun stop(): String? = send("ptzStopRun")

    private suspend fun send(cmd: String): String? = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                val url = "http://${cam.host.trim()}:${cam.onvifPort}/cgi-bin/CGIProxy.fcgi" +
                    "?cmd=$cmd&usr=${enc(cam.user)}&pwd=${enc(cam.password)}"
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.requestMethod = "GET"
                    val code = conn.responseCode
                    val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()?.use { it.readText() } ?: ""
                    if (code !in 200..299) {
                        "Foscam HTTP $code"
                    } else {
                        when (val r = Regex("<result>\\s*(-?\\d+)\\s*</result>").find(text)?.groupValues?.get(1)) {
                            null, "0" -> null
                            "-2" -> "Benutzer oder Passwort falsch"
                            "-3" -> "Zugriff verweigert"
                            else -> "Foscam-Fehler $r"
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
