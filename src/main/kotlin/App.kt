import auth.azureConfig
import auth.issuerProperties
import io.javalin.Javalin
import io.javalin.plugin.json.JavalinJackson
import mottasvar.SvarService
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.veilarbaktivitet.avro.DelingAvCvRespons
import no.nav.veilarbaktivitet.avro.ForesporselOmDelingAvCv
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.producer.KafkaProducer
import sendforespørsel.ForespørselService
import sendforespørsel.UsendtScheduler
import sendforespørsel.producerConfig
import stilling.AccessTokenClient
import stilling.StillingClient
import utils.Cluster
import utils.log
import utils.objectMapper
import utils.settCallId
import java.io.Closeable
import kotlin.concurrent.thread

class App(
    private val controller: Controller,
    private val issuerProperties: IssuerProperties,
    private val scheduler: UsendtScheduler,
    private val svarService: SvarService,
) : Closeable {

    init {
        JavalinJackson.configure(objectMapper)
    }

    private val webServer = Javalin.create().apply {
        config.defaultContentType = "application/json"
        before(validerToken(issuerProperties))
        before(settCallId)
        routes {
            get("/internal/isAlive") { it.status(if (svarService.isOk()) 200 else 500) }
            get("/internal/isReady") { it.status(200) }
            get("/foresporsler/:$stillingsIdParamName", controller.hentForespørsler)
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

        val svarConsumer = MockConsumer<String, DelingAvCvRespons>(OffsetResetStrategy.EARLIEST) // TODO: Bruk KafkaProducer m/ consumerConfig
        val svarService = SvarService(svarConsumer, repository::oppdaterMedSvar)

        App(controller, issuerProperties, UsendtScheduler(database.dataSource,forespørselService::sendUsendte), svarService).start()

    } catch (exception: Exception) {
        log("main()").error("Noe galt skjedde", exception)
    }
}
