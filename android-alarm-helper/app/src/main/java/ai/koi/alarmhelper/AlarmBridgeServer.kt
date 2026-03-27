package ai.koi.alarmhelper

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class AlarmBridgeServer(
    private val port: Int,
    private val tokenProvider: () -> String,
    private val onAlarmRequest: (BridgeRequest) -> BridgeResponse
) : NanoHTTPD(port) {

    data class BridgeRequest(
        val hour: String?,
        val minute: String?,
        val label: String?,
        val days: String?,
        val skipUi: String?,
        val vibrate: String?,
        val soundType: String?,
        val autoLaunch: String?,
        val callbackUrl: String?,
        val source: String
    )

    data class BridgeResponse(
        val ok: Boolean,
        val message: String,
        val extra: Map<String, Any?> = emptyMap(),
        val status: Int = 200
    )

    override fun serve(session: IHTTPSession): Response {
        return try {
            if (session.method != Method.GET && session.method != Method.POST) {
                jsonResponse(405, false, "Only GET/POST supported")
            } else if (session.uri != "/set") {
                jsonResponse(404, false, "Use /set endpoint")
            } else {
                if (session.method == Method.POST) {
                    val tmpFiles = HashMap<String, String>()
                    session.parseBody(tmpFiles)
                }

                val params = session.parameters.mapValues { it.value.firstOrNull() }
                val providedToken =
                    params["token"] ?: session.headers["x-alarm-token"] ?: session.headers["X-Alarm-Token"]
                val requiredToken = tokenProvider().trim()

                if (requiredToken.isNotEmpty() && providedToken != requiredToken) {
                    jsonResponse(401, false, "Invalid or missing token")
                } else {
                    val bridgeRequest = BridgeRequest(
                        hour = params["hour"],
                        minute = params["minute"],
                        label = params["label"],
                        days = params["days"],
                        skipUi = params["skipUi"],
                        vibrate = params["vibrate"],
                        soundType = params["soundType"],
                        autoLaunch = params["autoLaunch"],
                        callbackUrl = params["callback"],
                        source = params["source"] ?: "bridge"
                    )

                    val response = onAlarmRequest(bridgeRequest)
                    jsonResponse(response.status, response.ok, response.message, response.extra)
                }
            }
        } catch (t: Throwable) {
            jsonResponse(500, false, "Bridge error: ${t.message ?: "unknown"}")
        }
    }

    private fun jsonResponse(
        statusCode: Int,
        ok: Boolean,
        message: String,
        extra: Map<String, Any?> = emptyMap()
    ): Response {
        val payload = JSONObject().apply {
            put("ok", ok)
            put("message", message)
            put("status", statusCode)
            extra.forEach { (k, v) -> put(k, v) }
        }.toString()

        return newFixedLengthResponse(Response.Status.lookup(statusCode), "application/json", payload).apply {
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Cache-Control", "no-store")
        }
    }
}
