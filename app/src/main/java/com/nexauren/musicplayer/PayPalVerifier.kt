package com.musicplayer.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    suspend fun verifyWithRetry(
        uid: String,
        plan: PremiumPlan,
        orderId: String? = null,
        subscriptionId: String? = null,
        maxAttempts: Int = 5
    ): Result<PayPalVerificationResult> {
        var last = Result.failure<PayPalVerificationResult>(
            IllegalStateException("PayPal verification did not run.")
        )
        repeat(maxAttempts.coerceAtLeast(1)) { attempt ->
            last = verify(uid, plan, orderId, subscriptionId)
            val result = last.getOrNull()
            if (last.isSuccess && result?.verified == true) return last
            if (attempt < maxAttempts - 1) {
                delay(1_000L * (attempt + 1))
            }
        }
        return last
    }

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

data class PendingPayPalPurchase(
    val plan: PremiumPlan,
    val orderId: String?,
    val subscriptionId: String?
)

object PayPalCheckout {
    private const val PREFS = "paypal_pending_purchase"
    private const val PLAN = "plan"
    private const val ORDER_ID = "orderId"
    private const val SUBSCRIPTION_ID = "subscriptionId"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun savePending(context: Context, plan: PremiumPlan, orderId: String?, subscriptionId: String?) {
        prefs(context).edit()
            .putString(PLAN, plan.name)
            .putString(ORDER_ID, orderId)
            .putString(SUBSCRIPTION_ID, subscriptionId)
            .apply()
    }

    fun loadPending(context: Context): PendingPayPalPurchase? {
        val raw = prefs(context).getString(PLAN, null) ?: return null
        val plan = runCatching { PremiumPlan.valueOf(raw) }.getOrNull() ?: return null
        return PendingPayPalPurchase(
            plan,
            prefs(context).getString(ORDER_ID, null),
            prefs(context).getString(SUBSCRIPTION_ID, null)
        )
    }

    fun clearPending(context: Context) {
        prefs(context).edit().clear().apply()
    }

    suspend fun createCheckout(context: Context, uid: String, plan: PremiumPlan): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = BuildConfig.PAYPAL_WORKER_URL.trim().trimEnd('/')
                require(base.isNotBlank()) { "PayPal checkout service is not configured." }
                val path = when (plan) {
                    PremiumPlan.QUARTERLY -> "/paypal/checkout/quarterly"
                    PremiumPlan.LIFETIME -> "/paypal/checkout/lifetime"
                    PremiumPlan.NONE -> error("Choose a Premium plan.")
                }
                val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/json")
                }
                connection.outputStream.use { it.write(JSONObject().put("uid", uid).toString().toByteArray(Charsets.UTF_8)) }
                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                require(responseCode in 200..299) {
                    runCatching { JSONObject(body).optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }
                        ?: "PayPal checkout returned HTTP $responseCode."
                }
                val json = JSONObject(body)
                val approvalUrl = json.optString("approveUrl").takeIf { it.isNotBlank() }
                    ?: error("PayPal did not return an approval URL.")
                savePending(
                    context,
                    plan,
                    json.optString("orderId").takeIf { it.isNotBlank() },
                    json.optString("subscriptionId").takeIf { it.isNotBlank() }
                )
                approvalUrl
            }
        }

    suspend fun verifyPending(
        context: Context,
        uid: String,
        premiumRepository: PremiumRepository
    ): Result<Boolean>? {
        val pending = loadPending(context) ?: return null
        return PayPalVerifier.verifyWithRetry(uid, pending.plan, pending.orderId, pending.subscriptionId).map { result ->
            if (result.verified) {
                premiumRepository.setVerifiedFromWorker(
                    result.plan, result.expiresAtMillis, result.orderId, result.subscriptionId
                )
                clearPending(context)
                true
            } else false
        }
    }
}
