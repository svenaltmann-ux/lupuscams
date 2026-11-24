package de.example.lupuscams

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/** Speichert die Kameras (inkl. Passwörter) verschlüsselt über den Android Keystore. */
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
                    name = o.optString("name"),
                    host = o.optString("host"),
                    port = o.optInt("port", 554),
                    user = o.optString("user", "admin"),
                    password = o.optString("password"),
                    pathTemplate = o.optString("path", Camera.DEFAULT_PATH),
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
                    .put("name", c.name)
                    .put("host", c.host)
                    .put("port", c.port)
                    .put("user", c.user)
                    .put("password", c.password)
                    .put("path", c.pathTemplate)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "cameras"
    }
}
