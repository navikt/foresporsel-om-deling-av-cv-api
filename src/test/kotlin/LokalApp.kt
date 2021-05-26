import no.nav.security.token.support.core.configuration.IssuerProperties
import java.net.URL

fun main() {
    startLokalApp()
}

fun startLokalApp(repository: Repository = Repository(TestDatabase().dataSource)): App {
    val service = Service(repository)

    val issuerProperties = IssuerProperties(
        URL("http://localhost:18300/default/.well-known/openid-configuration"),
        listOf("default"),
        "isso-idtoken"
    )

    val app = App(service, issuerProperties)

    app.start()

    return app
}
