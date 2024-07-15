package auth

import utils.Miljø

class AccessTokenClient(private val config: AzureConfig,  tokenCache: TokenCache) : TokenClient(config, tokenCache) {
    private val cacheKey = "access_token"

    fun getAccessToken(): String {
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

        return getToken(cacheKey, formData)
    }
}