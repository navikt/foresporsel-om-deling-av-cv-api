import io.javalin.Javalin
import no.nav.security.token.support.core.configuration.IssuerProperties
import utils.Cluster
import utils.log
import java.io.Closeable
import java.net.URL

class App(service: Service, issuerProperties: IssuerProperties) : Closeable {

    private val webServer = Javalin.create().apply {
        config.defaultContentType = "application/json"
        before(validerToken(issuerProperties))
        routes {
            get("/internal/isAlive") { it.status(200) }
            get("/internal/isReady") { it.status(200) }
            post("/foresporsler", service.lagreForespørselOmDelingAvCv)
        }
    }

    fun start() {
        try {
            webServer.start(8333)

        } catch (exception: Exception) {
            close()
            throw exception
        }
    }

    override fun close() {
        webServer.stop()
    }
}

fun main() {

    try {
        val database = Database()
        val service = Service()

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

        App(service, issuerProperties).start()

    } catch (exception: Exception) {
        log("main()").error("Noe galt skjedde", exception)
    }
}
