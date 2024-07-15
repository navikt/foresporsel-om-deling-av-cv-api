import auth.*
import io.javalin.Javalin
import io.javalin.plugin.json.JavalinJackson
import kandidatevent.DelCvMedArbeidsgiverLytter
import kandidatevent.KandidatlisteLukketLytter
import kandidatevent.RegistrertFåttJobbenLytter
import mottasvar.SvarService
import mottasvar.consumerConfig
import no.nav.foresporselomdelingavcv.avroProducerConfig
import no.nav.foresporselomdelingavcv.jsonProducerConfig
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.veilarbaktivitet.avro.DelingAvCvRespons
import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.ForesporselOmDelingAvCv
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import sendforespørsel.ForespørselService
import sendforespørsel.UsendtScheduler
import stilling.StillingKlient
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import utils.Miljø
import utils.log
import utils.objectMapper
import utils.settCallId
import java.io.Closeable
import java.util.*
import kotlin.concurrent.thread

class App(
    private val forespørselController: ForespørselController,
    private val svarstatistikkController: SvarstatistikkController,
    private val issuerProperties: List<IssuerProperties>,
    private val scheduler: UsendtScheduler,
    private val svarService: SvarService,
    private val rapidsConnection: RapidsConnection,
    private val tokenValidationContextHolder: TokenValidationContextHolder,
    private val kandidatsokApiKlient: KandidatsokApiKlient
) : Closeable {

    init {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"))
    }

    private val webServer = Javalin.create { config ->
        config.defaultContentType = "application/json"
        config.jsonMapper(JavalinJackson(objectMapper))
    }.apply {
        before(validerToken(issuerProperties))
        before(settCallId)
        routes {
            get("/internal/isAlive") { it.status(if (svarService.isOk()) 200 else 500) }
            get("/internal/isReady") { it.status(200) }
            get("/foresporsler/kandidat/{$aktorIdParamName}", forespørselController.hentForespørslerForKandidat) // historikkside, rad, hendelsesetikett, synlig for den som kan se historikken
            get("/foresporsler/{$stillingsIdParamName}", forespørselController.hentForespørsler) // Brukes i kandidatlisten, ved visning av feilmeldinger for sende forespørsler, kun arbeidsgiverrettet/utvikler
            post("/foresporsler", forespørselController.sendForespørselOmDelingAvCv) // For sending av forespørsler, kun arbeidgiverrettet/utvikler
            post("/foresporsler/kandidat/{$aktorIdParamName}", forespørselController.resendForespørselOmDelingAvCv) // Kun arbeidsgiverrettet/utvikler
            get("/statistikk", svarstatistikkController.hentSvarstatistikk) // På forsiden, tilgjengelig for alle
        }
    }

    fun start() {
        try {
            webServer.start(8333)
            scheduler.kjørPeriodisk()
            thread { svarService.start() }
            thread { rapidsConnection.start() }
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
        log("main").info("Starter app i cluster ${Miljø.current.asString()}")

        val database = Database()
        val repository = Repository(database.dataSource)
        val tokenValidationContextHolder = SimpleTokenValidationContextHolder()
        val tokenService = TokenService(tokenValidationContextHolder)
        val accessTokenClient = AccessTokenClient(azureConfig)
        val oboTokenClient = OnBehalfOfTokenClient(azureConfig.azureClientId, azureConfig.azureClientSecret, azureConfig.tokenEndpoint, tokenService)
        val stillingKlient = StillingKlient(accessTokenClient::getAccessToken)
        val kandidatsokApiKlient = KandidatsokApiKlient(oboTokenClient)

        val forespørselProducer = KafkaProducer<String, ForesporselOmDelingAvCv>(avroProducerConfig)
        val forespørselService = ForespørselService(
            forespørselProducer,
            repository,
            stillingKlient::hentStilling,
        )

        val usendtScheduler = UsendtScheduler(database.dataSource, forespørselService::sendUsendte)
        val forespørselController =
            ForespørselController(repository, usendtScheduler::kjørEnGang, stillingKlient::hentStilling, kandidatsokApiKlient::verifiserKandidatTilgang)
        val svarstatistikkController = SvarstatistikkController(repository)

        val env = System.getenv()
        lateinit var rapidIsAlive: () -> Boolean
        val rapidsConnection = RapidApplication.create(env, configure = { _, kafkarapid ->
            rapidIsAlive = kafkarapid::isRunning
        })

        val statusoppdateringProducer = KafkaProducer<String, String>(jsonProducerConfig)
        val ptoTopic = "pto.rekrutteringsbistand-statusoppdatering-v1"
        KandidatlisteLukketLytter(rapidsConnection, ptoTopic, statusoppdateringProducer, repository)
        DelCvMedArbeidsgiverLytter(rapidsConnection, ptoTopic, statusoppdateringProducer, repository)
        RegistrertFåttJobbenLytter(rapidsConnection, ptoTopic, statusoppdateringProducer, repository)

        val svarConsumer = KafkaConsumer<String, DelingAvCvRespons>(consumerConfig)
        val svarService = SvarService(svarConsumer, repository, rapidIsAlive)

        App(
            forespørselController,
            svarstatistikkController,
            listOf(azureIssuerProperties),
            usendtScheduler,
            svarService,
            rapidsConnection,
            tokenValidationContextHolder,
            kandidatsokApiKlient
        ).start()

    } catch (exception: Exception) {
        log("main()").error("Noe galt skjedde", exception)
    }
}
