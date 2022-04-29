package auth

import io.javalin.core.security.RouteRole
import navalin.RoleConfig
import no.nav.security.token.support.core.configuration.IssuerProperties
import java.net.URL

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
    URL(System.getenv("AZURE_APP_WELL_KNOWN_URL")),
    listOf(System.getenv("AZURE_APP_CLIENT_ID")),
    System.getenv("AZURE_OPENID_CONFIG_ISSUER")
)

enum class Rolle : RouteRole {
    ALLE
}

val routeRoleConfigs = listOf(
    RoleConfig(
        role = Rolle.ALLE,
        issuerProperties = listOf(azureIssuerProperties),
        necessaryTokenClaims = listOf("NAVident")
    )
)