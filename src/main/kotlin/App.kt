import io.javalin.Javalin
import utils.log
import java.io.Closeable

class App(service: Service) : Closeable {

    private val webServer = Javalin.create().apply {
        config.defaultContentType = "application/json"
        before(validerToken)
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
        App(Service()).start()

    } catch (exception: Exception) {
        log("main()").error("Noe galt skjedde", exception)
    }
}
