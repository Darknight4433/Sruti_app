package com.sruti.music.auth

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(private val context: Context) {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _userProfile = MutableStateFlow(loadSavedProfile() ?: mapUser(auth.currentUser))
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val saved = loadSavedProfile()
            val profile = mapUser(firebaseAuth.currentUser)
            if (saved != null && saved.isAuthenticated) {
                // Preserved Google/Local authenticated profile takes priority over anonymous Firebase sessions
                _userProfile.value = saved
            } else if (profile.isAuthenticated) {
                _userProfile.value = profile
            } else {
                _userProfile.value = saved ?: profile
            }
            Log.d(TAG, "Auth state updated: authenticated=${_userProfile.value.isAuthenticated}, name=${_userProfile.value.displayName}, email=${_userProfile.value.email}")
        }
    }

    fun setGoogleAccount(
        email: String,
        displayName: String? = null,
        photoUrl: String? = null
    ) {
        val trimmedEmail = email.trim()
        val derivedName = if (!displayName.isNullOrBlank()) {
            displayName.trim()
        } else {
            val prefix = trimmedEmail.substringBefore("@")
            prefix.split(".", "_", "-")
                .map { part ->
                    val letters = part.filter { it.isLetter() }
                    if (letters.isNotEmpty()) {
                        letters.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    } else part
                }
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { prefix }
        }

        val effectivePhoto = if (!photoUrl.isNullOrBlank()) {
            photoUrl
        } else {
            val hash = try {
                val md = java.security.MessageDigest.getInstance("MD5")
                java.math.BigInteger(1, md.digest(trimmedEmail.lowercase().toByteArray())).toString(16).padStart(32, '0')
            } catch (e: Exception) { "" }
            if (hash.isNotEmpty()) "https://www.gravatar.com/avatar/$hash?s=256&d=identicon" else null
        }

        val profile = UserProfile(
            isAuthenticated = true,
            uid = "google_${trimmedEmail.lowercase().hashCode().toString().replace("-", "x")}",
            displayName = derivedName,
            email = trimmedEmail,
            photoUrl = effectivePhoto,
            isAnonymous = false,
            providerId = "google.com"
        )
        _userProfile.value = profile
        val prefs = context.getSharedPreferences("sruti_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("user_uid", profile.uid)
            .putString("user_name", profile.displayName)
            .putString("user_email", profile.email)
            .putString("user_photo", profile.photoUrl)
            .putBoolean("is_authenticated", true)
            .apply()
        Log.d(TAG, "Google account set: ${profile.displayName} (${profile.email}), photo: ${profile.photoUrl}")

        // Also initiate background Firebase anonymous auth session if needed for Firestore/storage tokens
        if (auth.currentUser == null) {
            scope.launch {
                runCatching {
                    auth.signInAnonymously().await()
                    Log.d(TAG, "Background Firebase anonymous token initialized")
                }
            }
        }
    }

    fun setGoogleProfile(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        val email = account.email ?: "google_user@gmail.com"
        val name = account.displayName ?: email.substringBefore("@")
        val photo = account.photoUrl?.toString()
        setGoogleAccount(email = email, displayName = name, photoUrl = photo)
    }

    suspend fun signInWithGoogleProvider(activity: android.app.Activity): Result<UserProfile> {
        return try {
            val provider = com.google.firebase.auth.OAuthProvider.newBuilder("google.com").build()
            val result = auth.startActivityForSignInWithProvider(activity, provider).await()
            val user = result.user
            val profile = mapUser(user)
            val prefs = context.getSharedPreferences("sruti_auth_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("user_uid", profile.uid)
                .putString("user_name", profile.displayName)
                .putString("user_email", profile.email)
                .putString("user_photo", profile.photoUrl)
                .putBoolean("is_authenticated", true)
                .apply()
            _userProfile.value = profile
            Log.d(TAG, "signInWithGoogleProvider success: ${profile.displayName} (${profile.email}), photo=${profile.photoUrl}")
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "signInWithGoogleProvider error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun loadSavedProfile(): UserProfile? {
        val prefs = context.getSharedPreferences("sruti_auth_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_authenticated", false)) return null
        val uid = prefs.getString("user_uid", null) ?: return null
        val name = prefs.getString("user_name", null)
        val email = prefs.getString("user_email", null)
        val photo = prefs.getString("user_photo", null)
        return UserProfile(
            isAuthenticated = true,
            uid = uid,
            displayName = name,
            email = email,
            photoUrl = photo,
            isAnonymous = false,
            providerId = "google.com"
        )
    }

    private fun mapUser(user: FirebaseUser?): UserProfile {
        return if (user != null) {
            UserProfile(
                isAuthenticated = true,
                uid = user.uid,
                displayName = user.displayName,
                email = user.email,
                photoUrl = user.photoUrl?.toString(),
                isAnonymous = user.isAnonymous,
                providerId = user.providerData.firstOrNull()?.providerId ?: "firebase"
            )
        } else {
            UserProfile(
                isAuthenticated = false,
                uid = null,
                displayName = null,
                email = null,
                photoUrl = null,
                isAnonymous = false,
                providerId = "none"
            )
        }
    }

    suspend fun signInWithGoogle(idToken: String): Result<UserProfile> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val profile = mapUser(result.user)
            _userProfile.value = profile
            Log.d(TAG, "signInWithGoogle success: ${profile.displayName} (${profile.email})")
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "signInWithGoogle failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<UserProfile> {
        return try {
            val result = auth.signInWithEmailAndPassword(email.trim(), pass).await()
            val profile = mapUser(result.user)
            _userProfile.value = profile
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "signInWithEmail failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, pass: String, displayName: String?): Result<UserProfile> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user
            if (user != null && !displayName.isNullOrBlank()) {
                val updates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName.trim())
                    .build()
                user.updateProfile(updates).await()
            }
            val profile = mapUser(auth.currentUser)
            _userProfile.value = profile
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "signUpWithEmail failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signInAnonymously(): Result<UserProfile> {
        return try {
            val result = auth.signInAnonymously().await()
            val profile = mapUser(result.user)
            _userProfile.value = profile
            Result.success(profile)
        } catch (e: Exception) {
            Log.e(TAG, "signInAnonymously failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateDisplayName(name: String): Result<Unit> {
        val trimmed = name.trim()
        val prefs = context.getSharedPreferences("sruti_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("user_name", trimmed).apply()

        val current = _userProfile.value
        _userProfile.value = current.copy(displayName = trimmed)

        val user = auth.currentUser
        if (user != null) {
            try {
                val updates = UserProfileChangeRequest.Builder()
                    .setDisplayName(trimmed)
                    .build()
                user.updateProfile(updates).await()
            } catch (e: Exception) {
                Log.w(TAG, "Firebase updateProfile non-critical exception: ${e.message}")
            }
        }
        return Result.success(Unit)
    }

    suspend fun updatePhotoUrl(photoUrl: String): Result<Unit> {
        val trimmed = photoUrl.trim()
        val prefs = context.getSharedPreferences("sruti_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("user_photo", trimmed).apply()

        val current = _userProfile.value
        _userProfile.value = current.copy(photoUrl = trimmed)

        val user = auth.currentUser
        if (user != null) {
            try {
                val updates = UserProfileChangeRequest.Builder()
                    .setPhotoUri(android.net.Uri.parse(trimmed))
                    .build()
                user.updateProfile(updates).await()
            } catch (e: Exception) {
                Log.w(TAG, "Firebase updatePhotoUri non-critical exception: ${e.message}")
            }
        }
        return Result.success(Unit)
    }

    fun signOut() {
        try {
            auth.signOut()
            val prefs = context.getSharedPreferences("sruti_auth_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            _userProfile.value = mapUser(null)
            try {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                GoogleSignIn.getClient(context, gso).signOut()
            } catch (ge: Exception) {
                Log.w(TAG, "GoogleSignIn client signOut warning: ${ge.message}")
            }
            Log.d(TAG, "User signed out")
        } catch (e: Exception) {
            Log.e(TAG, "signOut failed: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "FirebaseAuthRepo"

        @Volatile
        private var instance: FirebaseAuthRepository? = null

        fun getInstance(context: Context): FirebaseAuthRepository {
            return instance ?: synchronized(this) {
                instance ?: FirebaseAuthRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
