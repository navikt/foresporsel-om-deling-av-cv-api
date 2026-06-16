package auth

import no.nav.security.token.support.core.configuration.IssuerProperties
import java.net.URI

data class AzureConfig (
    val azureClientSecret: String,
    val azureClientId: String,
    val tokenEndpoint: String
)

val azureConfig = AzureConfig(
    System.getenv("AZURE_APP_CLIENT_SECRET"),
    System.getenv("AZURE_APP_CLIENT_ID"),
    System.getenv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")
)

val azureIssuerProperties = IssuerProperties(
    URI(System.getenv("AZURE_APP_WELL_KNOWN_URL")).toURL(),
    listOf(System.getenv("AZURE_APP_CLIENT_ID")),
    System.getenv("AZURE_OPENID_CONFIG_ISSUER")
)
val tokenxIssuerProperties = IssuerProperties(
    URI(System.getenv("TOKEN_X_WELL_KNOWN_URL")).toURL(),
    listOf(System.getenv("TOKEN_X_CLIENT_ID")),
    System.getenv("TOKEN_X_ISSUER")
)

