package com.nexauren.musicplayer2

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class AccountSnapshot(
    val uid: String,
    val email: String?,
    val displayName: String?
)

class FirebaseAccountRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("account_state", Context.MODE_PRIVATE)

    fun isFirebaseConfigured(): Boolean =
        FirebaseApp.getApps(context).isNotEmpty()

    fun currentAccount(): AccountSnapshot? {
        if (!isFirebaseConfigured()) return null
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return AccountSnapshot(user.uid, user.email, user.displayName)
    }

    suspend fun signInWithEmail(email: String, password: String): Result<AccountSnapshot> {
        if (!isFirebaseConfigured()) {
            return Result.failure(IllegalStateException("Firebase is not configured yet."))
        }
        return runCatching {
            val user = FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(email.trim(), password)
                .await()
                .user ?: error("Could not sign in.")
            persist(user.uid)
            AppAnalytics.login("email")
            AccountSnapshot(user.uid, user.email, user.displayName)
        }
    }

    suspend fun createWithEmail(email: String, password: String): Result<AccountSnapshot> {
        if (!isFirebaseConfigured()) {
            return Result.failure(IllegalStateException("Firebase is not configured yet."))
        }
        return runCatching {
            val user = FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(email.trim(), password)
                .await()
                .user ?: error("Could not create account.")
            persist(user.uid)
            AppAnalytics.signUp("email")
            AccountSnapshot(user.uid, user.email, user.displayName)
        }
    }

    suspend fun signInWithGoogle(activity: Activity): Result<AccountSnapshot> {
        if (!isFirebaseConfigured()) {
            return Result.failure(IllegalStateException("Firebase is not configured yet."))
        }

        val webClientIdResId = activity.resources.getIdentifier(
            "default_web_client_id",
            "string",
            activity.packageName
        )
        if (webClientIdResId == 0) {
            return Result.failure(
                IllegalStateException("Firebase Google Sign-In is not configured yet.")
            )
        }

        return runCatching {
            val webClientId = activity.getString(webClientIdResId)
            val credentialManager = CredentialManager.create(activity)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activity, request)
            val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
            val credential = GoogleAuthProvider.getCredential(
                googleCredential.idToken,
                null
            )

            val user = FirebaseAuth.getInstance()
                .signInWithCredential(credential)
                .await()
                .user ?: error("Could not sign in with Google.")

            persist(user.uid)
            AppAnalytics.login("google")
            AccountSnapshot(user.uid, user.email, user.displayName)
        }
    }

    suspend fun updateDisplayName(name: String): Result<AccountSnapshot> {
        if (!isFirebaseConfigured()) return Result.failure(IllegalStateException("Firebase is not configured yet."))
        val clean = name.trim()
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Name cannot be empty."))
        return runCatching {
            val user = FirebaseAuth.getInstance().currentUser ?: error("No account is signed in.")
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(clean).build()).await()
            AppAnalytics.log("profile_update")
            AccountSnapshot(user.uid, user.email, user.displayName)
        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        if (!isFirebaseConfigured()) return Result.failure(IllegalStateException("Firebase is not configured yet."))
        return runCatching {
            val user = FirebaseAuth.getInstance().currentUser ?: error("No account is signed in.")
            user.delete().await()
            prefs.edit().clear().apply()
            AppAnalytics.log("account_deleted")
        }
    }

    fun signOut() {
        if (isFirebaseConfigured()) {
            FirebaseAuth.getInstance().signOut()
        }
        prefs.edit().clear().apply()
        AppAnalytics.log("logout")
    }

    private fun persist(uid: String) {
        prefs.edit().putString("uid", uid).apply()
    }
}

enum class PremiumPlan {
    NONE,
    QUARTERLY,
    LIFETIME,
    SHARED
}

data class PremiumSnapshot(
    val plan: PremiumPlan = PremiumPlan.NONE,
    val expiresAtMillis: Long? = null,
    val verified: Boolean = false,
    val orderId: String? = null,
    val subscriptionId: String? = null,
    val cloudSynced: Boolean = false
) {
    val isActive: Boolean
        get() = cloudSynced && verified && when (plan) {
            PremiumPlan.LIFETIME -> true
            PremiumPlan.QUARTERLY ->
                expiresAtMillis == null || expiresAtMillis > System.currentTimeMillis()
            PremiumPlan.SHARED -> true
            PremiumPlan.NONE -> false
        }
}

class PremiumRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("premium_state", Context.MODE_PRIVATE)

    private fun currentUid(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    fun loadLocal(): PremiumSnapshot {
        val ownerUid = prefs.getString("ownerUid", null)
        val uid = currentUid()
        if (ownerUid.isNullOrBlank() || uid.isNullOrBlank() || ownerUid != uid) {
            return PremiumSnapshot()
        }

        val rawPlan = prefs.getString("plan", PremiumPlan.NONE.name) ?: PremiumPlan.NONE.name
        val plan = runCatching { PremiumPlan.valueOf(rawPlan) }.getOrDefault(PremiumPlan.NONE)
        val expires = prefs.getLong("expiresAt", 0L).takeIf { it > 0L }
        val verified = prefs.getBoolean("verified", false)
        val orderId = prefs.getString("orderId", null)
        val subscriptionId = prefs.getString("subscriptionId", null)
        val cloudSynced = prefs.getBoolean("cloudSynced", false)
        return PremiumSnapshot(plan, expires, verified, orderId, subscriptionId, cloudSynced)
    }

    fun isPremium(): Boolean = loadLocal().isActive


    fun setVerifiedFromWorker(
        plan: PremiumPlan,
        expiresAtMillis: Long?,
        orderId: String?,
        subscriptionId: String?
    ) {
        val uid = currentUid() ?: throw IllegalStateException("No Firebase account is signed in.")
        prefs.edit()
            .putString("ownerUid", uid)
            .putString("plan", plan.name)
            .putLong("expiresAt", expiresAtMillis ?: 0L)
            .putBoolean("verified", true)
            .putString("orderId", orderId)
            .putString("subscriptionId", subscriptionId)
            .putBoolean("cloudSynced", false)
            .apply()
    }

    fun clearVerifiedPremium() {
        prefs.edit()
            .putString("plan", PremiumPlan.NONE.name)
            .putLong("expiresAt", 0L)
            .putBoolean("verified", false)
            .putBoolean("cloudSynced", false)
            .remove("ownerUid")
            .remove("orderId")
            .remove("subscriptionId")
            .apply()
    }

    /**
     * Refreshes the read-only entitlement from Firestore.
     *
     * The Cloudflare Worker is the only component allowed to write
     * verified Premium entitlements. The Android client only reads them.
     */
    suspend fun syncVerifiedToFirebase(): Result<Unit> {
        return runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                throw IllegalStateException("Firebase is not configured.")
            }
            if (FirebaseAuth.getInstance().currentUser == null) {
                throw IllegalStateException("No Firebase account is signed in.")
            }

            prefs.edit().putBoolean("cloudSynced", false).apply()
            syncFromFirebase().getOrThrow()

            val state = loadLocal()
            if (!state.cloudSynced) {
                throw IllegalStateException("Firebase Premium entitlement is not available yet.")
            }
            AppAnalytics.log("firestore_entitlement_verified")
        }.onFailure {
            AppAnalytics.log(
                "firestore_sync_error",
                "source" to "premium_entitlement",
                "error_type" to (it::class.simpleName ?: "unknown")
            )
        }
    }

    suspend fun syncFromFirebase(): Result<Unit> {
        if (FirebaseApp.getApps(context).isEmpty()) return Result.success(Unit)
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success(Unit)

        return runCatching {
            val firestore = FirebaseFirestore.getInstance()
            val userDoc = firestore.collection("users").document(user.uid).get().await()

            // Cross-app Premium source of truth: users/{uid}.premium.
            // Music Player historically used only users/{uid}/entitlement/premium,
            // which caused valid Premium accounts written by another Nexauren app
            // to appear as Free when that subcollection was empty.
            if (userDoc.exists() && userDoc.contains("premium")) {
                val premium = userDoc.getBoolean("premium") == true
                if (premium) {
                    val rootPlan = when (userDoc.getString("plan")?.lowercase()) {
                        "lifetime", "premium-lifetime" -> PremiumPlan.LIFETIME
                        "quarterly", "premium-quarterly" -> PremiumPlan.QUARTERLY
                        else -> PremiumPlan.SHARED
                    }
                    prefs.edit()
                        .putString("ownerUid", user.uid)
                        .putString("plan", rootPlan.name)
                        .putLong("expiresAt", userDoc.getLong("expiresAtMillis") ?: 0L)
                        .putBoolean("verified", true)
                        .putString("orderId", userDoc.getString("orderId"))
                        .putString("subscriptionId", userDoc.getString("paypalSubscriptionId"))
                        .putBoolean("cloudSynced", true)
                        .apply()
                    AppAnalytics.log(
                        "firestore_premium_sync",
                        "source" to "users_document",
                        "premium" to "true",
                        "plan" to rootPlan.name.lowercase()
                    )
                    return@runCatching
                } else {
                    prefs.edit()
                        .putString("ownerUid", user.uid)
                        .putString("plan", PremiumPlan.NONE.name)
                        .putLong("expiresAt", 0L)
                        .putBoolean("verified", false)
                        .putBoolean("cloudSynced", true)
                        .remove("orderId")
                        .remove("subscriptionId")
                        .apply()
                    AppAnalytics.log("firestore_premium_sync", "source" to "users_document", "premium" to "false")
                    return@runCatching
                }
            }

            // Backward-compatible fallback for Music Player's own entitlement document.
            val doc = userDoc.reference
                .collection("entitlement")
                .document("premium")
                .get()
                .await()

            if (!doc.exists()) {
                prefs.edit()
                    .putString("ownerUid", user.uid)
                    .putString("plan", PremiumPlan.NONE.name)
                    .putLong("expiresAt", 0L)
                    .putBoolean("verified", false)
                    .putBoolean("cloudSynced", false)
                    .remove("orderId")
                    .remove("subscriptionId")
                    .apply()
                AppAnalytics.log("firestore_entitlement_missing")
                return@runCatching
            }

            val plan = when (doc.getString("plan")) {
                "quarterly" -> PremiumPlan.QUARTERLY
                "lifetime" -> PremiumPlan.LIFETIME
                else -> PremiumPlan.NONE
            }
            val expires = doc.getLong("expiresAtMillis")
            val verified = doc.getBoolean("verified") ?: false
            val orderId = doc.getString("orderId")
            val subscriptionId = doc.getString("subscriptionId")

            prefs.edit()
                .putString("ownerUid", user.uid)
                .putString("plan", plan.name)
                .putLong("expiresAt", expires ?: 0L)
                .putBoolean("verified", verified)
                .putString("orderId", orderId)
                .putString("subscriptionId", subscriptionId)
                .putBoolean("cloudSynced", true)
                .apply()
            AppAnalytics.log(
                "firestore_entitlement_sync",
                "plan" to plan.name.lowercase(),
                "verified" to verified.toString()
            )
        }.onFailure {
            AppAnalytics.log(
                "firestore_sync_error",
                "source" to "premium_entitlement",
                "error_type" to (it::class.simpleName ?: "unknown")
            )
        }
    }

}
