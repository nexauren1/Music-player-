package com.musicplayer.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PayPalSubscriptionManager {
    suspend fun manage(context: Context, uid: String, subscriptionId: String, action: String): Result<Boolean> =
        runCatching {
            val payload = JSONObject()
                .put("uid", uid)
                .put("subscriptionId", subscriptionId)
            val endpoint = BuildConfig.PAYPAL_WORKER_URL.trimEnd('/') +
                "/paypal/subscription/" + action
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            if (code !in 200..299) {
                throw IllegalStateException(JSONObject(body).optString("error").ifBlank { "PayPal request failed." })
            }
            JSONObject(body).optBoolean("ok", false)
        }
}
