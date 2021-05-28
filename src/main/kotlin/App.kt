import auth.azureConfig
import auth.issuerProperties
import io.javalin.Javalin
import no.nav.rekrutteringsbistand.avro.ForesporselOmDelingAvCvKafkamelding
import no.nav.security.token.support.core.configuration.IssuerProperties
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.Producer
import sendforespørsel.KafkaService
import sendforespørsel.UsendtScheduler
import stilling.AccessTokenClient
import stilling.StillingClient
import utils.Cluster
import utils.log
import utils.settCallId
import java.io.Closeable

class App(
    private val controller: Controller,
    private val issuerProperties: IssuerProperties,
    private val kafkaService: KafkaService,
    private val scheduler: UsendtScheduler
) : Closeable {

    private val webServer = Javalin.create().apply {
        config.defaultContentType = "application/json"
        before(validerToken(issuerProperties))
        before(settCallId)
        routes {
            get("/internal/isAlive") { it.status(200) }
            get("/internal/isReady") { it.status(200) }
            post("/foresporsler", controller.lagreForespørselOmDelingAvCv)
        }
    }

    fun start() {
        try {
            webServer.start(8333)

            scheduler.kjørPeriodisk()
            log.info("App startet")
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
        log("main").info("Starter app i cluster ${Cluster.current.asString()}")

        val database = Database()
        val repository = Repository(database.dataSource)
        val controller = Controller(repository)

        // TODO: Bytt til ekte producer
        val producer: Producer<String, ForesporselOmDelingAvCvKafkamelding> = MockProducer(true, null, null)

        val accessTokenClient = AccessTokenClient(azureConfig)
        val stillingClient = StillingClient(accessTokenClient::getAccessToken)
        val kafkaService = KafkaService(producer, repository, stillingClient::hentStilling)

        App(controller, issuerProperties, kafkaService, UsendtScheduler(database.dataSource,kafkaService::sendUsendteForespørsler)).start()

    } catch (exception: Exception) {
        log("main()").error("Noe galt skjedde", exception)
    }
}
