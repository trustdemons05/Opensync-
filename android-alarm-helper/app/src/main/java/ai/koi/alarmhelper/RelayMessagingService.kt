package ai.koi.alarmhelper

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.net.HttpURLConnection
import java.net.URL

class RelayMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        RelayConfigStore.saveDeviceToken(this, token)
        RelayConfigStore.saveLastEvent(this, "Relay token refreshed")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val payload = remoteMessage.data
        if (payload.isEmpty()) {
            RelayConfigStore.saveLastEvent(this, "Relay message ignored: empty payload")
            return
        }

        val config = RelayConfigStore.load(this)
        val required = config.sharedToken.trim()
        val provided = payload["token"].orEmpty().trim()
        val tokenValid = required.isBlank() || (provided.isNotBlank() && provided == required)

        val result = AlarmCommandExecutor.scheduleFromMap(
            payload = payload,
            source = payload["source"] ?: "relay",
            tokenIsValid = tokenValid,
            context = this
        )

        RelayConfigStore.saveLastEvent(this, result.message)

        val callbackUrl = payload["callback"]?.takeIf { it.isNotBlank() }
        if (callbackUrl != null) {
            sendCallbackAsync(
                callbackUrl = callbackUrl,
                ok = result.ok,
                message = result.message,
                extra = mapOf(
                    "triggerAt" to (result.triggerAt?.toString().orEmpty()),
                    "alarmId" to (result.alarmId ?: "")
                )
            )
        }
    }

    private fun sendCallbackAsync(
        callbackUrl: String,
        ok: Boolean,
        message: String,
        extra: Map<String, String> = emptyMap()
    ) {
        Thread {
            try {
                val conn = (URL(callbackUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 7000
                    readTimeout = 7000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                val payload = buildString {
                    append("{\"ok\":")
                    append(if (ok) "true" else "false")
                    append(",\"message\":\"")
                    append(message.replace("\"", "\\\""))
                    append("\"")
                    extra.forEach { (k, v) ->
                        append(",\"")
                        append(k.replace("\"", "\\\""))
                        append("\":\"")
                        append(v.replace("\"", "\\\""))
                        append("\"")
                    }
                    append("}")
                }

                conn.outputStream.use { it.write(payload.toByteArray()) }
                conn.inputStream.close()
                conn.disconnect()
            } catch (_: Throwable) {
                // best effort callback
            }
        }.start()
    }
}
