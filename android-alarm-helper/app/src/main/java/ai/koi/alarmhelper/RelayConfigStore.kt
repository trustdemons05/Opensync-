package ai.koi.alarmhelper

import android.content.Context

data class RelayConfig(
    val projectId: String = "",
    val applicationId: String = "",
    val apiKey: String = "",
    val senderId: String = "",
    val sharedToken: String = "",
    val lastDeviceToken: String = "",
    val lastEvent: String = ""
) {
    fun hasFirebaseCoreFields(): Boolean {
        return projectId.isNotBlank() &&
            applicationId.isNotBlank() &&
            apiKey.isNotBlank() &&
            senderId.isNotBlank()
    }
}

object RelayConfigStore {
    private const val PREFS = "koi_relay_store"

    private const val KEY_PROJECT_ID = "projectId"
    private const val KEY_APPLICATION_ID = "applicationId"
    private const val KEY_API_KEY = "apiKey"
    private const val KEY_SENDER_ID = "senderId"
    private const val KEY_SHARED_TOKEN = "sharedToken"
    private const val KEY_LAST_DEVICE_TOKEN = "lastDeviceToken"
    private const val KEY_LAST_EVENT = "lastEvent"

    fun load(context: Context): RelayConfig {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return RelayConfig(
            projectId = prefs.getString(KEY_PROJECT_ID, "").orEmpty(),
            applicationId = prefs.getString(KEY_APPLICATION_ID, "").orEmpty(),
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            senderId = prefs.getString(KEY_SENDER_ID, "").orEmpty(),
            sharedToken = prefs.getString(KEY_SHARED_TOKEN, "").orEmpty(),
            lastDeviceToken = prefs.getString(KEY_LAST_DEVICE_TOKEN, "").orEmpty(),
            lastEvent = prefs.getString(KEY_LAST_EVENT, "").orEmpty()
        )
    }

    fun save(context: Context, config: RelayConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROJECT_ID, config.projectId.trim())
            .putString(KEY_APPLICATION_ID, config.applicationId.trim())
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_SENDER_ID, config.senderId.trim())
            .putString(KEY_SHARED_TOKEN, config.sharedToken.trim())
            .apply()
    }

    fun updateSharedToken(context: Context, sharedToken: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SHARED_TOKEN, sharedToken.trim())
            .apply()
    }

    fun saveDeviceToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_DEVICE_TOKEN, token)
            .apply()
    }

    fun saveLastEvent(context: Context, event: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_EVENT, event)
            .apply()
    }
}
