import io.javalin.Javalin
import utils.Cluster
import utils.log
import java.io.Closeable
import javax.sql.DataSource

class App(dataSource: DataSource) : Closeable {

    private val webServer = Javalin.create().apply {
        config.defaultContentType = "application/json"
        routes {
            get("/internal/isAlive") { it.status(200) }
            get("/internal/isReady") { it.status(200) }
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

        App(database.dataSource).start()
    } catch (exception: Exception) {
        log("main()").error("Noe galt skjedde", exception)
    }
}
