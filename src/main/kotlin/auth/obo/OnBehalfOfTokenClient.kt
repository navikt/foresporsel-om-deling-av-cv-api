package auth.obo

import auth.AzureConfig
import auth.TokenCache
import auth.TokenClient
import auth.TokenHandler

class OnBehalfOfTokenClient(private val config: AzureConfig, private val tokenHandler: TokenHandler, tokenCache: TokenCache) : TokenClient(config, tokenCache) {

    companion object {
        const val AZURE_ON_BEHALF_OF_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val REQUESTED_TOKEN_USE = "on_behalf_of"
    }

    fun getOboToken(motScope: String, navIdent: String): String {
        val cacheKey = "$motScope-$navIdent"
        val innkommendeToken = tokenHandler.hentTokenSomString()

        val formData = listOf(
            "grant_type" to AZURE_ON_BEHALF_OF_GRANT_TYPE,
            "client_id" to config.azureClientId,
            "client_secret" to config.azureClientSecret,
            "assertion" to innkommendeToken,
            "scope" to motScope,
            "requested_token_use" to REQUESTED_TOKEN_USE
        )

        return getToken(cacheKey, formData)
    }
}