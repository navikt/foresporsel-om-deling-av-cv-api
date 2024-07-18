package auth

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import org.ehcache.Cache
import org.ehcache.CacheManager
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ExpiryPolicyBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import utils.log
import java.time.Duration
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
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(formData.joinToString("&") { "${it.first}=${it.second}" })
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TokenResponse(
        val access_token: String,
        val expires_in: Int,
    )
}

class TokenCache(private val expiryMarginSeconds: Long = 30L) {
    private val cacheManager: CacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true)
    private val cache: Cache<String, CachedToken> = cacheManager.createCache("tokenCache",
        CacheConfigurationBuilder.newCacheConfigurationBuilder(
            String::class.java, CachedToken::class.java,
            ResourcePoolsBuilder.heap(5000)
        ).withExpiry(ExpiryPolicyBuilder.timeToIdleExpiration(Duration.ofSeconds(expiryMarginSeconds)))
            .build()
    )

    fun getToken(key: String): String? {
        val cachedToken = cache.get(key)
        return if (cachedToken != null && !cachedToken.isExpired(expiryMarginSeconds)) {
            cachedToken.token
        } else {
            null
        }
    }

    fun putToken(key: String, token: String, expiresInSeconds: Long) {
        val expiryTime = LocalDateTime.now().plusSeconds(expiresInSeconds)
        cache.put(key, CachedToken(token, expiryTime))
    }

    private data class CachedToken(val token: String, val expiryTime: LocalDateTime) {
        fun isExpired(marginSeconds: Long): Boolean {
            return expiryTime.minusSeconds(marginSeconds).isBefore(LocalDateTime.now())
        }
    }
}
