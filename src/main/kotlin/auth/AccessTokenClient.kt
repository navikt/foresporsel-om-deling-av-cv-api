package auth

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import utils.Miljø
import java.time.LocalDateTime

class AccessTokenClient(private val config: AzureConfig, private val tokenService: TokenService) {
    private lateinit var cachedAccessToken: CachedAccessToken

    fun getAccessToken(): String {
        if (!this::cachedAccessToken.isInitialized || cachedAccessToken.erUtgått()) {
            cachedAccessToken = nyttToken()
        }
        return cachedAccessToken.accessToken
    }

    fun getOboToken(motScope: String, navIdent: String): String {
        val azureADKlient = AzureADKlient(config.azureClientId, config.azureClientSecret, config.tokenEndpoint, tokenService)
        return azureADKlient.onBehalfOfToken(motScope, navIdent)
    }

    private fun nyttToken(): CachedAccessToken {
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
            is Result.Success -> result.get().somCachedToken()
            is Result.Failure -> throw RuntimeException("Noe feil skjedde ved henting av access_token: ", result.getException())
        }
    }

    private class CachedAccessToken(
        val accessToken: String,
        private val utgår: LocalDateTime,
    ) {
        private val utløpsmarginSekunder = 30L
        fun erUtgått() = utgår.minusSeconds(utløpsmarginSekunder).isBefore(LocalDateTime.now())
    }

    private data class AccessToken(
        val token_type: String,
        val expires_in: Int,
        val ext_expires_in: Int,
        val access_token: String
    ) {
        fun somCachedToken() = CachedAccessToken(access_token, LocalDateTime.now().plusSeconds(expires_in.toLong()))
    }
}