package auth

import getenv
import no.nav.security.token.support.core.configuration.IssuerProperties
import java.net.URI

data class AzureConfig (
    val azureClientSecret: String,
    val azureClientId: String,
    val tokenEndpoint: String
)

val azureConfig = AzureConfig(
    getenv("AZURE_APP_CLIENT_SECRET"),
    getenv("AZURE_APP_CLIENT_ID"),
    getenv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")
)

val azureIssuerProperties = IssuerProperties(
    URI(getenv("AZURE_APP_WELL_KNOWN_URL")).toURL(),
    listOf(getenv("AZURE_APP_CLIENT_ID")),
    getenv("AZURE_OPENID_CONFIG_ISSUER")
)
val tokenxIssuerProperties = IssuerProperties(
    URI(getenv("TOKEN_X_WELL_KNOWN_URL")).toURL(),
    listOf(getenv("TOKEN_X_CLIENT_ID")),
    getenv("TOKEN_X_ISSUER")
)

