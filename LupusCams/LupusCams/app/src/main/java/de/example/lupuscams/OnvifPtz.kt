package de.example.lupuscams

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Minimaler ONVIF-Client zum Schwenken/Neigen (ContinuousMove und Stop).
 * Alle Aufrufe geben null zurück, wenn sie geklappt haben, sonst eine kurze Fehlermeldung.
 */
interface PtzController {
    suspend fun move(pan: Float, tilt: Float): String?
    suspend fun stop(): String?
}

class OnvifPtz(private val cam: Camera) : PtzController {

    private val lock = Mutex()
    private var ptzUrl: String? = null
    private var profileToken: String? = null
    private var timeOffsetMs: Long = 0L
    private var ready = false

    private val base get() = "http://${cam.host.trim()}:${cam.onvifPort}"

    override suspend fun move(pan: Float, tilt: Float): String? = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                prepare()
                val body = """<tptz:ContinuousMove xmlns:tptz="$NS_PTZ">
<tptz:ProfileToken>$profileToken</tptz:ProfileToken>
<tptz:Velocity><tt:PanTilt xmlns:tt="$NS_SCHEMA" x="$pan" y="$tilt"/></tptz:Velocity>
</tptz:ContinuousMove>"""
                post(ptzUrl!!, body, auth = true)
                null
            } catch (e: Exception) {
                ready = false
                e.message ?: e.javaClass.simpleName
            }
        }
    }

    override suspend fun stop(): String? = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                prepare()
                val body = """<tptz:Stop xmlns:tptz="$NS_PTZ">
<tptz:ProfileToken>$profileToken</tptz:ProfileToken>
<tptz:PanTilt>true</tptz:PanTilt><tptz:Zoom>true</tptz:Zoom>
</tptz:Stop>"""
                post(ptzUrl!!, body, auth = true)
                null
            } catch (e: Exception) {
                ready = false
                e.message ?: e.javaClass.simpleName
            }
        }
    }

    /** Ermittelt Zeitabweichung, Dienst-Adressen und Profil-Token (einmal pro Kamera). */
    private fun prepare() {
        if (ready) return

        // 1. Uhrzeit der Kamera (viele Kameras prüfen die Zeit im Anmeldetoken)
        timeOffsetMs = try {
            val xml = post(
                "$base/onvif/device_service",
                "<tds:GetSystemDateAndTime xmlns:tds=\"$NS_DEVICE\"/>",
                auth = false,
            )
            val block = Regex("UTCDateTime>(.*?)</(?:\\w+:)?UTCDateTime", RegexOption.DOT_MATCHES_ALL)
                .find(xml)?.groupValues?.get(1)
            if (block == null) {
                0L
            } else {
                fun f(n: String) =
                    Regex("<(?:\\w+:)?$n>(\\d+)<").find(block)?.groupValues?.get(1)?.toInt() ?: 0
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                cal.set(f("Year"), f("Month") - 1, f("Day"), f("Hour"), f("Minute"), f("Second"))
                cal.timeInMillis - System.currentTimeMillis()
            }
        } catch (e: Exception) {
            0L
        }

        // 2. Dienst-Adressen (Medien und PTZ); bei Fehlern die üblichen Standardpfade
        var mediaUrl = "$base/onvif/media_service"
        var ptz = "$base/onvif/ptz_service"
        try {
            val caps = post(
                "$base/onvif/device_service",
                "<tds:GetCapabilities xmlns:tds=\"$NS_DEVICE\"><tds:Category>All</tds:Category></tds:GetCapabilities>",
                auth = true,
            )
            Regex("<(?:\\w+:)?Media>\\s*<(?:\\w+:)?XAddr>([^<]+)</", RegexOption.DOT_MATCHES_ALL)
                .find(caps)?.groupValues?.get(1)?.let { mediaUrl = it.trim() }
            Regex("<(?:\\w+:)?PTZ>\\s*<(?:\\w+:)?XAddr>([^<]+)</", RegexOption.DOT_MATCHES_ALL)
                .find(caps)?.groupValues?.get(1)?.let { ptz = it.trim() }
        } catch (e: IOException) {
            // Standardpfade verwenden
        }
        ptzUrl = ptz

        // 3. Profil-Token (erstes Medienprofil)
        val profiles = post(mediaUrl, "<trt:GetProfiles xmlns:trt=\"$NS_MEDIA\"/>", auth = true)
        profileToken = Regex("Profiles[^>]*token=\"([^\"]+)\"").find(profiles)?.groupValues?.get(1)
            ?: throw IOException("Kein ONVIF-Profil gefunden")
        ready = true
    }

    private fun securityHeader(): String {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        val created = fmt.format(Date(System.currentTimeMillis() + timeOffsetMs))
        val md = MessageDigest.getInstance("SHA-1")
        md.update(nonce)
        md.update(created.toByteArray(Charsets.UTF_8))
        md.update(cam.password.toByteArray(Charsets.UTF_8))
        val digest = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
        val nonceB64 = Base64.encodeToString(nonce, Base64.NO_WRAP)
        return """<s:Header><Security s:mustUnderstand="1" xmlns="$NS_WSSE">
<UsernameToken><Username>${xml(cam.user)}</Username>
<Password Type="$NS_PWD_DIGEST">$digest</Password>
<Nonce EncodingType="$NS_NONCE_B64">$nonceB64</Nonce>
<Created xmlns="$NS_WSU">$created</Created></UsernameToken></Security></s:Header>"""
    }

    private fun post(url: String, body: String, auth: Boolean): String {
        val envelope = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">")
            if (auth) append(securityHeader())
            append("<s:Body>").append(body).append("</s:Body></s:Envelope>")
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8")
            conn.outputStream.use { it.write(envelope.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IOException("ONVIF HTTP $code")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private companion object {
        const val NS_DEVICE = "http://www.onvif.org/ver10/device/wsdl"
        const val NS_MEDIA = "http://www.onvif.org/ver10/media/wsdl"
        const val NS_PTZ = "http://www.onvif.org/ver20/ptz/wsdl"
        const val NS_SCHEMA = "http://www.onvif.org/ver10/schema"
        const val NS_WSSE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
        const val NS_WSU =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
        const val NS_PWD_DIGEST =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest"
        const val NS_NONCE_B64 =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary"
    }
}
