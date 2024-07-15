package auth.obo

import auth.AzureConfig
import auth.TokenCache
import auth.TokenHandler
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import utils.log

class OnBehalfOfTokenClient(private val config: AzureConfig, private val tokenHandler: TokenHandler, private val tokenCache: TokenCache) {
    private val objectMapper = jacksonObjectMapper()

    companion object {
        const val AZURE_ON_BEHALF_OF_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val REQUESTED_TOKEN_USE = "on_behalf_of"
    }

    fun getOboToken(motScope: String, navIdent: String): String {
        val cacheKey = "$motScope-$navIdent"
        val cachedToken = tokenCache.getToken(cacheKey)
        if (cachedToken != null) {
            return cachedToken
        }

        val newToken = fetchNewOboToken(motScope)
        tokenCache.putToken(cacheKey, newToken.access_token, newToken.expires_in.toLong())
        return newToken.access_token
    }

    private fun fetchNewOboToken(motScope: String): TokenResponse {
        val innkommendeToken = tokenHandler.hentTokenSomString()

        val formData = listOf(
            "grant_type" to AZURE_ON_BEHALF_OF_GRANT_TYPE,
            "client_id" to config.azureClientId,
            "client_secret" to config.azureClientSecret,
            "assertion" to innkommendeToken,
            "scope" to motScope,
            "requested_token_use" to REQUESTED_TOKEN_USE
        )

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
                log.error("Feil ved henting av OBO-token: ", result.getException())
                throw RuntimeException("Feil ved henting av OBO-token", result.getException())
            }
        }
    }

    private data class TokenResponse(
        val access_token: String,
        val expires_in: Int
    )
}