package com.musicplayer.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PayPalVerificationResult(
    val verified: Boolean,
    val plan: PremiumPlan,
    val expiresAtMillis: Long?,
    val orderId: String?,
    val subscriptionId: String?,
    val message: String = ""
)

object PayPalVerifier {
    suspend fun verify(
        uid: String,
        plan: PremiumPlan,
        orderId: String? = null,
        subscriptionId: String? = null
    ): Result<PayPalVerificationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val base = BuildConfig.PAYPAL_WORKER_URL.trim().trimEnd('/')
            require(base.isNotBlank()) { "PayPal verification service is not configured." }

            val url = URL(
                base + "/paypal/verify?plan=" + java.net.URLEncoder.encode(plan.name.lowercase(), "UTF-8") +
                    "&uid=" + java.net.URLEncoder.encode(uid, "UTF-8") +
                    (orderId?.takeIf { it.isNotBlank() }?.let {
                        "&orderId=" + java.net.URLEncoder.encode(it, "UTF-8")
                    } ?: "") +
                    (subscriptionId?.takeIf { it.isNotBlank() }?.let {
                        "&subscriptionId=" + java.net.URLEncoder.encode(it, "UTF-8")
                    } ?: "")
            )

            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()

            require(responseCode in 200..299) {
                "Verification service returned HTTP $responseCode."
            }

            val json = JSONObject(body)
            val verified = json.optBoolean("verified", false)
            val verifiedPlan = when (json.optString("plan")) {
                "lifetime" -> PremiumPlan.LIFETIME
                "quarterly" -> PremiumPlan.QUARTERLY
                else -> PremiumPlan.NONE
            }

            PayPalVerificationResult(
                verified = verified,
                plan = verifiedPlan,
                expiresAtMillis = if (json.isNull("expiresAtMillis")) null else json.optLong("expiresAtMillis").takeIf { it > 0L },
                orderId = json.optString("orderId").takeIf { it.isNotBlank() },
                subscriptionId = json.optString("subscriptionId").takeIf { it.isNotBlank() },
                message = json.optString("paypalStatus")
            )
        }
    }
}
