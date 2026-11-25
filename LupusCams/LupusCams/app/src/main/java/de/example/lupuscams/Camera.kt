package de.example.lupuscams

import android.net.Uri
import java.util.UUID

/**
 * Eine Kamera. Lupus (LE202/LE204/LE221/LE232/LE200) und Imou (z. B. Rex 3D) nutzen den
 * Dahua-typischen Pfad /cam/realmonitor?channel=1&subtype=0 (0 = Hauptstream, 1 = Substream).
 * "%d" im Pfad wird durch 0 (Haupt) bzw. 1 (Sub) ersetzt.
 */
data class Camera(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String = "",
    val port: Int = 554,
    val user: String = "admin",
    val password: String = "",
    val pathTemplate: String = PATH_STANDARD,
    /** Im Vollbild gleich den Substream nehmen (falls der Hauptstream nicht abspielbar ist). */
    val preferSub: Boolean = false,
    /** Schwenken/Neigen per ONVIF anzeigen. */
    val ptz: Boolean = false,
    /** Steuer-Port der Kamera (ONVIF bzw. Foscam-Weboberfläche, meist 80). */
    val onvifPort: Int = 80,
    /** Art der Schwenk-Steuerung: PTZ_ONVIF oder PTZ_FOSCAM. */
    val ptzMode: String = PTZ_ONVIF,
) {
    fun rtspUri(main: Boolean): Uri? {
        if (host.isBlank()) return null
        // Pfad entweder mit %d (0 = Haupt, 1 = Sub) oder als "Hauptpfad|Subpfad"
        val path = if (pathTemplate.contains("|")) {
            val parts = pathTemplate.split("|").map { it.trim() }
            if (main) parts[0] else parts.getOrElse(1) { parts[0] }
        } else {
            try {
                pathTemplate.format(if (main) 0 else 1)
            } catch (e: Exception) {
                pathTemplate
            }
        }
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val auth = if (user.isNotBlank()) "${Uri.encode(user)}:${Uri.encode(password)}@" else ""
        return Uri.parse("rtsp://$auth${host.trim()}:$port$cleanPath")
    }

    companion object {
        const val PATH_STANDARD = "/cam/realmonitor?channel=1&subtype=%d"
        const val PATH_ONVIF = "/cam/realmonitor?channel=1&subtype=%d&unicast=true&proto=Onvif"
        const val PATH_FOSCAM = "/videoMain|/videoSub"
        const val DEFAULT_PATH = PATH_STANDARD
        const val PTZ_ONVIF = "onvif"
        const val PTZ_FOSCAM = "foscam"

        fun defaults() = listOf(
            Camera(name = "LE232"),
            Camera(name = "LE221"),
            Camera(name = "LE204"),
            Camera(name = "LE202"),
        )
    }
}
