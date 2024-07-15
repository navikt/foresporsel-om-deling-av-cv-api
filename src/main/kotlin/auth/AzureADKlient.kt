package auth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import utils.log

class AzureADKlient(
    private val clientId: String,
    private val clientSecret: String,
    private val tokenEndpoint: String,
    private val tokenService: TokenService
) {
    private val azureCache = AzureCache()
    private val objectMapper = jacksonObjectMapper()

    companion object {
        const val AZURE_ON_BEHALF_OF_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val REQUESTED_TOKEN_USE = "on_behalf_of"
    }

    fun onBehalfOfToken(motScope: String, navIdent: String): String {
        val cachedToken = azureCache.hentOBOToken(motScope, navIdent)
        if (cachedToken != null) return cachedToken

        val innkommendeToken = tokenService.hentTokenSomString()

        val formData = listOf(
            "grant_type" to AZURE_ON_BEHALF_OF_GRANT_TYPE,
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "assertion" to innkommendeToken,
            "scope" to motScope,
            "requested_token_use" to REQUESTED_TOKEN_USE
        )

        val (_, response, result) = Fuel.post(tokenEndpoint)
            .body(formData.joinToString("&") { "${it.first}=${it.second}" })
            .header("Content-Type" to "application/x-www-form-urlencoded")
            .response()

        return when (result) {
            is Result.Success -> {
                val responseBody = response.body().asString("application/json")
                try {
                    val tokenResponse = objectMapper.readValue(responseBody, TokenResponse::class.java)
                    azureCache.lagreOBOToken(motScope, navIdent, tokenResponse.access_token)
                    tokenResponse.access_token
                } catch (e: Exception) {
                    log.error("Feil ved parsing av JSON-respons: ", e)
                    throw RuntimeException("Feil ved parsing av JSON-respons", e)
                }
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

class AzureCache {
    private val cache = mutableMapOf<String, String>()

    fun hentOBOToken(otherApp: String, onBehalfOf: String): String? {
        val key = "$otherApp-$onBehalfOf"
        return cache[key]
    }

    fun lagreOBOToken(otherApp: String, onBehalfOf: String, token: String) {
        val key = "$otherApp-$onBehalfOf"
        cache[key] = token
    }
}