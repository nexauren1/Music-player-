package com.musicplayer.app

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
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
            AccountSnapshot(user.uid, user.email, user.displayName)
        }
    }

    fun signOut() {
        if (isFirebaseConfigured()) {
            FirebaseAuth.getInstance().signOut()
        }
        prefs.edit().clear().apply()
    }

    private fun persist(uid: String) {
        prefs.edit().putString("uid", uid).apply()
    }
}

enum class PremiumPlan {
    NONE,
    QUARTERLY,
    LIFETIME
}

data class PremiumSnapshot(
    val plan: PremiumPlan = PremiumPlan.NONE,
    val expiresAtMillis: Long? = null,
    val verified: Boolean = false
)

class PremiumRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("premium_state", Context.MODE_PRIVATE)

    fun loadLocal(): PremiumSnapshot {
        val rawPlan = prefs.getString("plan", PremiumPlan.NONE.name) ?: PremiumPlan.NONE.name
        val plan = runCatching { PremiumPlan.valueOf(rawPlan) }.getOrDefault(PremiumPlan.NONE)
        val expires = prefs.getLong("expiresAt", 0L).takeIf { it > 0L }
        val verified = prefs.getBoolean("verified", false)
        return PremiumSnapshot(plan, expires, verified)
    }

    fun isPremium(): Boolean {
        val state = loadLocal()
        return when (state.plan) {
            PremiumPlan.LIFETIME -> state.verified
            PremiumPlan.QUARTERLY -> state.verified &&
                (state.expiresAtMillis == null || state.expiresAtMillis > System.currentTimeMillis())
            PremiumPlan.NONE -> false
        }
    }

    fun remainingPreviewUses(): Int =
        prefs.getInt("preview_uses", 0).coerceAtLeast(0)

    fun consumePreviewUse(): Boolean {
        val used = remainingPreviewUses()
        if (used >= TEST_PREMIUM_PREVIEW_LIMIT) return false
        prefs.edit().putInt("preview_uses", used + 1).apply()
        return true
    }

    suspend fun syncFromFirebase() {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return

        runCatching {
            val doc = FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.uid)
                .collection("entitlement")
                .document("premium")
                .get()
                .await()

            val plan = when (doc.getString("plan")) {
                "quarterly" -> PremiumPlan.QUARTERLY
                "lifetime" -> PremiumPlan.LIFETIME
                else -> PremiumPlan.NONE
            }
            val expires = doc.getLong("expiresAtMillis")
            val verified = doc.getBoolean("verified") ?: false

            prefs.edit()
                .putString("plan", plan.name)
                .putLong("expiresAt", expires ?: 0L)
                .putBoolean("verified", verified)
                .apply()
        }
    }

    companion object {
        const val TEST_PREMIUM_PREVIEW_LIMIT = 3
    }
}
