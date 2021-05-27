import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.jackson.responseObject
import java.time.LocalDateTime
import com.github.kittinunf.result.Result
import utils.Cluster


class AccessTokenClient(private val httpClient: FuelManager, private val config: AzureConfig) {
    private lateinit var cachedAccessToken : CachedAccessToken

    fun getAccessToken(): String {
        if (!this::cachedAccessToken.isInitialized || cachedAccessToken.erUtgått()) {
            cachedAccessToken = nyttToken()
        }
        return cachedAccessToken.accessToken
    }

    private fun nyttToken():CachedAccessToken {
        val formData = listOf(
            "grant_type" to "client_credentials",
            "client_secret" to config.azureClientSecret,
            "client_id" to config.azureClientId,
            "scope" to config.scope
        )

        val result = httpClient
            .post(config.tokenEndpoint, formData)
            .responseObject<AccessToken>().third

        when (result) {
            is Result.Success -> return result.get().somCachedToken()
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

class AzureConfig (
    val azureClientSecret: String,
    val azureClientId: String,
    val tokenEndpoint: String
) {
    val scope = when(Cluster.current) {
        Cluster.DEV_FSS -> "dev-gcp"
        Cluster.PROD_FSS -> "prod-gcp"
    }.let { cluster -> "api://${cluster}.arbeidsgiver.rekrutteringsbistand-stilling-proxy/.default" }
}
