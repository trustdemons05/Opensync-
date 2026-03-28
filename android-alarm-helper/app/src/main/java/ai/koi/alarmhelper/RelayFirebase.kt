package ai.koi.alarmhelper

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

object RelayFirebase {

    fun ensureInitialized(context: Context, config: RelayConfig): Result<FirebaseApp> {
        if (!config.hasFirebaseCoreFields()) {
            return Result.failure(
                IllegalArgumentException("Missing Firebase config. Fill project/app/api/sender fields first.")
            )
        }

        val existing = FirebaseApp.getApps(context).firstOrNull()
        if (existing != null) return Result.success(existing)

        return try {
            val options = FirebaseOptions.Builder()
                .setProjectId(config.projectId)
                .setApplicationId(config.applicationId)
                .setApiKey(config.apiKey)
                .setGcmSenderId(config.senderId)
                .build()

            val app = FirebaseApp.initializeApp(context, options)
                ?: return Result.failure(IllegalStateException("Firebase app init returned null"))
            Result.success(app)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun fetchRelayToken(context: Context, config: RelayConfig, callback: (Result<String>) -> Unit) {
        val init = ensureInitialized(context, config)
        val app = init.getOrNull()
        if (app == null) {
            callback(Result.failure(init.exceptionOrNull() ?: IllegalStateException("Firebase init failed")))
            return
        }

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> callback(Result.success(token)) }
            .addOnFailureListener { err -> callback(Result.failure(err)) }
    }
}
