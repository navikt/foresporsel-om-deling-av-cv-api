package auth

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import utils.Miljø
import java.time.LocalDateTime

class AccessTokenClient(private val config: AzureConfig, private val tokenCache: TokenCache) {
    private val cacheKey = "access_token"

    fun getAccessToken(): String {
        val cachedToken = tokenCache.getToken(cacheKey)
        if (cachedToken != null) {
            return cachedToken
        }

        val newToken = fetchNewToken()
        tokenCache.putToken(cacheKey, newToken.accessToken, newToken.expiresIn.toLong())
        return newToken.accessToken
    }

    private fun fetchNewToken(): AccessToken {
        val scope = when (Miljø.current) {
            Miljø.DEV_FSS -> "dev-gcp"
            Miljø.PROD_FSS -> "prod-gcp"
            Miljø.LOKAL -> "lokal"
        }.let { cluster -> "api://${cluster}.toi.rekrutteringsbistand-stillingssok-proxy/.default" }

        val formData = listOf(
            "grant_type" to "client_credentials",
            "client_secret" to config.azureClientSecret,
            "client_id" to config.azureClientId,
            "scope" to scope
        )

        val result = Fuel.post(config.tokenEndpoint, formData)
            .responseObject<AccessToken>().third

        return when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw RuntimeException("Noe feil skjedde ved henting av access_token: ", result.getException())
        }
    }

    private data class AccessToken(
        val token_type: String,
        val expires_in: Int,
        val ext_expires_in: Int,
        val access_token: String
    ) {
        val accessToken: String get() = access_token
        val expiresIn: Int get() = expires_in
    }
}