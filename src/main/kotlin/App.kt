import io.javalin.Javalin
import java.io.Closeable

class App : Closeable {

    private val webServer = Javalin.create().apply {
        config.defaultContentType = "application/json"
        routes {
            get("/internal/isAlive") { it.status(200) }
            get("/internal/isReady") { it.status(200) }
        }
    }

    fun start() {
        webServer.start(8333)
        print("Kjører app")
    }

    override fun close() {
        webServer.stop()
    }
}

fun main() {
    App().start()
}
