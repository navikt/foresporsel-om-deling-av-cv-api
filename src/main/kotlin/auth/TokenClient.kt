package auth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import utils.log
import java.time.LocalDateTime

abstract class TokenClient(
    private val config: AzureConfig,
    private val tokenCache: TokenCache
) {
    private val objectMapper = jacksonObjectMapper()

    fun getToken(cacheKey: String, formData: List<Pair<String, String>>): String {
        val cachedToken = tokenCache.getToken(cacheKey)
        if (cachedToken != null) {
            return cachedToken
        }

        val newToken = fetchNewToken(formData)
        tokenCache.putToken(cacheKey, newToken.access_token, newToken.expires_in.toLong())
        return newToken.access_token
    }

    private fun fetchNewToken(formData: List<Pair<String, String>>): TokenResponse {
        val (_, response, result) = Fuel.post(config.tokenEndpoint)
            .body(formData.joinToString("&") { "${it.first}=${it.second}" })
            .header("Content-Type" to "application/x-www-form-urlencoded")
            .response()

        return when (result) {
            is Result.Success -> {
                val responseBody = response.body().asString("application/json")
                objectMapper.readValue(responseBody, TokenResponse::class.java)
            }
            is Result.Failure -> {
                log.error("Feil ved henting av token: ", result.getException())
                throw RuntimeException("Feil ved henting av token", result.getException())
            }
        }
    }

    data class TokenResponse(
        val access_token: String,
        val expires_in: Int
    )
}

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