package de.example.lupuscams

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Speichert Kameras (inkl. Passwörter) und Einstellungen verschlüsselt über den Android Keystore. */
class CameraStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "cameras_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): List<Camera> {
        val raw = prefs.getString(KEY, null) ?: return Camera.defaults()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Camera(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = o.optString("name"),
                    host = o.optString("host"),
                    port = o.optInt("port", 554),
                    user = o.optString("user", "admin"),
                    password = o.optString("password"),
                    pathTemplate = o.optString("path", Camera.DEFAULT_PATH),
                    preferSub = o.optBoolean("preferSub", false),
                    ptz = o.optBoolean("ptz", false),
                    onvifPort = o.optInt("onvifPort", 80),
                    ptzMode = o.optString("ptzMode", Camera.PTZ_ONVIF),
                )
            }
        } catch (e: Exception) {
            Camera.defaults()
        }
    }

    fun save(cameras: List<Camera>) {
        val arr = JSONArray()
        cameras.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("host", c.host)
                    .put("port", c.port)
                    .put("user", c.user)
                    .put("password", c.password)
                    .put("path", c.pathTemplate)
                    .put("preferSub", c.preferSub)
                    .put("ptz", c.ptz)
                    .put("onvifPort", c.onvifPort)
                    .put("ptzMode", c.ptzMode)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun loadPerPage(): Int = prefs.getInt(KEY_PER_PAGE, 4)

    fun savePerPage(value: Int) {
        prefs.edit().putInt(KEY_PER_PAGE, value).apply()
    }

    private companion object {
        const val KEY = "cameras"
        const val KEY_PER_PAGE = "per_page"
    }
}
