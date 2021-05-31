package auth

import no.nav.security.token.support.core.configuration.IssuerProperties
import utils.Cluster
import java.net.URL

val issuerProperties = when (Cluster.current) {
    Cluster.DEV_FSS -> IssuerProperties(
        URL("https://login.microsoftonline.com/NAVQ.onmicrosoft.com/.well-known/openid-configuration"),
        listOf("38e07d31-659d-4595-939a-f18dce3446c5"),
        "isso-idtoken"
    )
    Cluster.PROD_FSS -> IssuerProperties(
        URL("https://login.microsoftonline.com/navno.onmicrosoft.com/.well-known/openid-configuration"),
        listOf("9b4e07a3-4f4c-4bab-b866-87f62dff480d"),
        "isso-idtoken"
    )
}