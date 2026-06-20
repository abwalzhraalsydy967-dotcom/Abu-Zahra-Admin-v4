package com.abuzahra.admin.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.abuzahra.admin.data.api.ApiClient

class Preferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "abu_zahra_admin_secure_prefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEFAULT_SERVER = "https://alsydyabwalzhra.online/"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_ONLINE_NOTIF = "notif_online"
        private const val KEY_OFFLINE_NOTIF = "notif_offline"
        private const val KEY_EVENT_NOTIF = "notif_events"
        private const val KEY_DARK_MODE = "dark_mode"

        // File manager — persisted sort mode (NAME / SIZE_DESC / DATE_DESC).
        // Defaults to NAME so the directory listing reads naturally.
        private const val KEY_FILES_SORT_MODE = "files_sort_mode"
        // Requested files activity sort mode (DATE / NAME / SIZE).
        private const val KEY_REQUESTED_FILES_SORT_MODE = "requested_files_sort_mode"
        // Last-browsed directory in FilesActivity — restored on next open.
        private const val KEY_FILES_LAST_PATH = "files_last_path"

        @Volatile
        private var instance: Preferences? = null

        fun getInstance(context: Context): Preferences {
            return instance ?: synchronized(this) {
                instance ?: Preferences(context.applicationContext).also { instance = it }
            }
        }
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, KEY_DEFAULT_SERVER) ?: KEY_DEFAULT_SERVER
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var isLoggedIn: Boolean
        get() = !token.isNullOrEmpty()
        set(_) {} // computed from token

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()

    var onlineNotifications: Boolean
        get() = prefs.getBoolean(KEY_ONLINE_NOTIF, true)
        set(value) = prefs.edit().putBoolean(KEY_ONLINE_NOTIF, value).apply()

    var offlineNotifications: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_NOTIF, true)
        set(value) = prefs.edit().putBoolean(KEY_OFFLINE_NOTIF, value).apply()

    var eventNotifications: Boolean
        get() = prefs.getBoolean(KEY_EVENT_NOTIF, true)
        set(value) = prefs.edit().putBoolean(KEY_EVENT_NOTIF, value).apply()

    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var permanentCode: String?
        get() = prefs.getString("permanent_code", null)
        set(value) = prefs.edit().putString("permanent_code", value).apply()

    var userEmail: String?
        get() = prefs.getString("user_email", null)
        set(value) = prefs.edit().putString("user_email", value).apply()

    var userName: String?
        get() = prefs.getString("user_name", null)
        set(value) = prefs.edit().putString("user_name", value).apply()

    var userRole: String?
        get() = prefs.getString("user_role", null)
        set(value) = prefs.edit().putString("user_role", value).apply()

    var userId: String?
        get() = prefs.getString("user_id", null)
        set(value) = prefs.edit().putString("user_id", value).apply()

    /**
     * Sort mode used by [com.abuzahra.admin.ui.files.FilesActivity] when
     * browsing the device filesystem. Stored as the enum name so we can
     * resiliently add new sort modes without format migrations.
     */
    var filesSortMode: String
        get() = prefs.getString(KEY_FILES_SORT_MODE, "NAME") ?: "NAME"
        set(value) = prefs.edit().putString(KEY_FILES_SORT_MODE, value).apply()

    /**
     * Sort mode used by [com.abuzahra.admin.ui.files.RequestedFilesActivity].
     * Defaults to DATE (newest first) which matches the prior behaviour.
     */
    var requestedFilesSortMode: String
        get() = prefs.getString(KEY_REQUESTED_FILES_SORT_MODE, "DATE") ?: "DATE"
        set(value) = prefs.edit().putString(KEY_REQUESTED_FILES_SORT_MODE, value).apply()

    /**
     * Last-browsed directory in the file manager. Restored on next open so
     * the admin picks up where they left off (e.g. mid-investigation).
     */
    var filesLastPath: String
        get() = prefs.getString(KEY_FILES_LAST_PATH, "/") ?: "/"
        set(value) = prefs.edit().putString(KEY_FILES_LAST_PATH, value).apply()

    fun getPermanentCodeOrEmpty(): String {
        return permanentCode ?: ""
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun getApiService(): com.abuzahra.admin.data.api.ApiService {
        return ApiClient.createWithToken(serverUrl, token ?: "")
    }

    fun getApiServiceWithoutToken(): com.abuzahra.admin.data.api.ApiService {
        return ApiClient.create(serverUrl)
    }
}