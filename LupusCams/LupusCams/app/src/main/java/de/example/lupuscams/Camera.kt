package de.example.lupuscams

import android.net.Uri

/**
 * Eine Kamera. Die Lupus-Modelle LE202/LE204/LE221 nutzen den Dahua-typischen Pfad
 * /cam/realmonitor?channel=1&subtype=0 (0 = Hauptstream, 1 = Substream).
 * Falls eine Kamera (z. B. LE232) einen anderen Pfad braucht, kann er in der App
 * pro Kamera geändert werden. "%d" wird durch 0 (Haupt) bzw. 1 (Sub) ersetzt.
 */
data class Camera(
    val name: String,
    val host: String = "",
    val port: Int = 554,
    val user: String = "admin",
    val password: String = "",
    val pathTemplate: String = DEFAULT_PATH,
) {
    fun rtspUri(main: Boolean): Uri? {
        if (host.isBlank()) return null
        val path = try {
            pathTemplate.format(if (main) 0 else 1)
        } catch (e: Exception) {
            pathTemplate
        }
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val auth = if (user.isNotBlank()) "${Uri.encode(user)}:${Uri.encode(password)}@" else ""
        return Uri.parse("rtsp://$auth${host.trim()}:$port$cleanPath")
    }

    companion object {
        const val DEFAULT_PATH = "/cam/realmonitor?channel=1&subtype=%d"

        fun defaults() = listOf(
            Camera(name = "LE232"),
            Camera(name = "LE221"),
            Camera(name = "LE204"),
            Camera(name = "LE202"),
        )
    }
}
