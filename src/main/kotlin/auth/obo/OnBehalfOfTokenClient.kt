package auth.obo

import auth.AzureConfig
import auth.TokenCache
import auth.TokenClient
import auth.TokenHandler
import io.javalin.http.Context
import utils.Miljø
import utils.Miljø.*
import utils.log

class OnBehalfOfTokenClient(private val config: AzureConfig, private val tokenHandler: TokenHandler, tokenCache: TokenCache) : TokenClient(config, tokenCache) {


    private val issuer = when (Miljø.current) {
        DEV_FSS -> "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0"
        PROD_FSS -> "https://login.microsoftonline.com/966ac572-f5b7-4bbe-aa88-c76419c0f851/v2.0"
        LOKAL -> "http://localhost:9089"
    }



    companion object {
        const val AZURE_ON_BEHALF_OF_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        const val REQUESTED_TOKEN_USE = "on_behalf_of"
    }

    fun hentTokenSomString(ctx: Context): String {
        val validerteTokens = tokenHandler.hentValiderteTokens(ctx)
        log.info("validertetokenissuers: ${validerteTokens.issuers}") // TODO fjern før produksjon

        // Filtrer ut riktig issuer før vi velger firstOrNull
        val issuerUrl = validerteTokens.issuers.firstOrNull { it == issuer }
            ?: throw RuntimeException("Ingen issuer funnet som matcher: $issuer")

        return validerteTokens.getJwtToken(issuerUrl)?.tokenAsString
            ?: throw RuntimeException("Ingen gyldig token funnet for issuer: $issuerUrl")
    }

    fun getOboToken(ctx: Context, motScope: String, navIdent: String): String {
        val cacheKey = "$motScope-$navIdent"
        val innkommendeToken = hentTokenSomString(ctx)

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
