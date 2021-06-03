import auth.azureConfig
import auth.issuerProperties
import io.javalin.Javalin
import mottasvar.SvarService
import mottasvar.consumerConfig
import no.nav.rekrutteringsbistand.avro.ForesporselOmDelingAvCv
import no.nav.rekrutteringsbistand.avro.SvarPaForesporselOmDelingAvCv
import no.nav.security.token.support.core.configuration.IssuerProperties
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import sendforespørsel.ForespørselService
import sendforespørsel.UsendtScheduler
import sendforespørsel.producerConfig
import stilling.AccessTokenClient
import stilling.StillingClient
import utils.Cluster
import utils.log
import utils.settCallId
import java.io.Closeable
import kotlin.concurrent.thread

class App(
    private val controller: Controller,
    private val issuerProperties: IssuerProperties,
    private val forespørselService: ForespørselService,
    private val scheduler: UsendtScheduler,
    private val svarService: SvarService,
) : Closeable {

    private val webServer = Javalin.create().apply {
        config.defaultContentType = "application/json"
        before(validerToken(issuerProperties))
        before(settCallId)
        routes {
            get("/internal/isAlive") { it.status(if (svarService.isOk()) 200 else 500) }
            get("/internal/isReady") { it.status(200) }
            post("/foresporsler", controller.lagreForespørselOmDelingAvCv)
        }
    }

    fun start() {
        try {
            webServer.start(8333)
            scheduler.kjørPeriodisk()
            thread { svarService.start() }

            log.info("App startet")
        } catch (exception: Exception) {
            close()
            throw exception
        }
    }

    override fun close() {
        svarService.close()
        webServer.stop()
    }
}

fun main() {

    try {
        log("main").info("Starter app i cluster ${Cluster.current.asString()}")

        val database = Database()
        val repository = Repository(database.dataSource)
        val controller = Controller(repository)

        val accessTokenClient = AccessTokenClient(azureConfig)
        val stillingClient = StillingClient(accessTokenClient::getAccessToken)

        val forespørselProducer = KafkaProducer<String, ForesporselOmDelingAvCv>(producerConfig)
        val forespørselService = ForespørselService(forespørselProducer, repository, stillingClient::hentStilling)

        val svarConsumer = KafkaConsumer<String, SvarPaForesporselOmDelingAvCv>(consumerConfig)
        val svarService = SvarService(svarConsumer, repository::oppdaterMedSvar) // TODO: Implementer consumer

        App(controller, issuerProperties, forespørselService, UsendtScheduler(database.dataSource,forespørselService::sendUsendte), svarService).start()

    } catch (exception: Exception) {
        log("main()").error("Noe galt skjedde", exception)
    }
}
