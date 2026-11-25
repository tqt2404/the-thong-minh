package utils

import java.util.prefs.Preferences

object AuthPrefs {
    private val prefs = Preferences.userRoot().node("parkcard_admin")

    fun saveToken(token: String) {
        prefs.put("token", token)
    }
    fun getToken(): String {
        return prefs.get("token", "")
    }
    fun clearToken() {
        prefs.remove("token")
    }
    fun isLoggedIn(): Boolean = getToken().isNotBlank()
}