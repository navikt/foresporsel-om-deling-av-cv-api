package auth

import no.nav.security.token.support.core.configuration.IssuerProperties
import utils.Miljø
import utils.Miljø.*
import java.net.URL

val issuerProperties = listOf(
    when (Miljø.current) {
        DEV_FSS -> IssuerProperties(
            URL("https://login.microsoftonline.com/NAVQ.onmicrosoft.com/.well-known/openid-configuration"),
            listOf("38e07d31-659d-4595-939a-f18dce3446c5"),
            "isso-idtoken"
        )
        PROD_FSS -> IssuerProperties(
            URL("https://login.microsoftonline.com/navno.onmicrosoft.com/.well-known/openid-configuration"),
            listOf("9b4e07a3-4f4c-4bab-b866-87f62dff480d"),
            "isso-idtoken"
        )
        LOKAL -> TODO()
    }, IssuerProperties(
        URL(System.getenv("AZURE_APP_WELL_KNOWN_URL")),
        listOf(System.getenv("AZURE_APP_CLIENT_ID")),
        System.getenv("AZURE_OPENID_CONFIG_ISSUER")
    )
)