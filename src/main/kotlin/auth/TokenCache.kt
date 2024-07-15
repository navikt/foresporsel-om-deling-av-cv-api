package auth

import java.time.LocalDateTime

class TokenCache(private val expiryMarginSeconds: Long = 30L) {
    private val cache = mutableMapOf<String, CachedToken>()

    fun getToken(key: String): String? {
        val cachedToken = cache[key]
        return if (cachedToken != null && !cachedToken.isExpired(expiryMarginSeconds)) {
            cachedToken.token
        } else {
            null
        }
    }

    fun putToken(key: String, token: String, expiresInSeconds: Long) {
        val expiryTime = LocalDateTime.now().plusSeconds(expiresInSeconds)
        cache[key] = CachedToken(token, expiryTime)
    }

    private data class CachedToken(val token: String, val expiryTime: LocalDateTime) {
        fun isExpired(marginSeconds: Long): Boolean {
            return expiryTime.minusSeconds(marginSeconds).isBefore(LocalDateTime.now())
        }
    }
}