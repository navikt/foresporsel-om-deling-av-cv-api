import no.nav.security.token.support.core.configuration.IssuerProperties
import java.net.URL

fun main() {
    startLokalApp()
}

fun startLokalApp(): App {
    val database = TestDatabase()
    val service = Service()
    val issuerProperties = IssuerProperties(
        URL("http://localhost:18300/isso-idtoken/.well-known/openid-configuration"),
        listOf("audience"),
        "isso-idtoken"
    )

    val app = App(service, issuerProperties)

    app.start()

    return app
}
