package com.sruti.music.auth

data class UserProfile(
    val isAuthenticated: Boolean = false,
    val uid: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val isAnonymous: Boolean = false,
    val providerId: String = "firebase"
) {
    /**
     * Initials for fallback avatar when photoUrl is null or unavailable.
     */
    val initials: String
        get() {
            val name = displayName?.trim()
            if (!name.isNullOrBlank()) {
                val parts = name.split(" ").filter { it.isNotBlank() }
                return if (parts.size >= 2) {
                    "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
                } else {
                    name.take(2).uppercase()
                }
            }
            val mail = email?.trim()
            if (!mail.isNullOrBlank()) {
                return mail.take(2).uppercase()
            }
            return if (isAuthenticated) "U" else "G"
        }
}
