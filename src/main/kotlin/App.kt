import auth.*
import auth.obo.KandidatsokApiKlient
import auth.obo.OnBehalfOfTokenClient
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
import no.nav.veilarbaktivitet.avro.DelingAvCvRespons
import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.ForesporselOmDelingAvCv
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import sendforespørsel.ForespørselService
import sendforespørsel.UsendtScheduler
import stilling.StillingKlient
import utils.Miljø
import utils.log
import utils.objectMapper
import utils.settCallId
import java.io.Closeable
import java.net.URL
import java.util.*
import kotlin.concurrent.thread

class App(
    private val forespørselController: ForespørselController,
    private val svarstatistikkController: SvarstatistikkController,
    private val scheduler: UsendtScheduler,
    private val svarService: SvarService,
    private val rapidsConnection: RapidsConnection,
    private val tokenHandler: TokenHandler,
) : Closeable {

    init {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"))
    }

    private val webServer = Javalin.create { config ->
        config.defaultContentType = "application/json"
        config.jsonMapper(JavalinJackson(objectMapper))
    }.apply {
        before(tokenHandler::validerToken)
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
        log("main").info("url:" + System.getenv("AZURE_APP_WELL_KNOWN_URL"))
        log("main").info("klientid:" + System.getenv("AZURE_APP_CLIENT_ID"))
        log("main").info("issuer:" + System.getenv("AZURE_OPENID_CONFIG_ISSUER"))
        val rollekeys = initierRollekeys()

        val database = Database()
        val repository = Repository(database.dataSource)
        val tokenHandler = TokenHandler(listOf(azureIssuerProperties), rollekeys)
        val tokenCache = TokenCache()
        val accessTokenClient = AccessTokenClient(azureConfig, tokenCache)
        val oboTokenClient = OnBehalfOfTokenClient(azureConfig, tokenHandler, tokenCache)
        val stillingKlient = StillingKlient(accessTokenClient::getAccessToken)
        val kandidatsokApiKlient = KandidatsokApiKlient(oboTokenClient)
        val autorisasjon = Autorisasjon(kandidatsokApiKlient)

        val forespørselProducer = KafkaProducer<String, ForesporselOmDelingAvCv>(avroProducerConfig)
        val forespørselService = ForespørselService(
            forespørselProducer,
            repository,
            stillingKlient::hentStilling,
        )

        val usendtScheduler = UsendtScheduler(database.dataSource, forespørselService::sendUsendte)
        val forespørselController =
            ForespørselController(repository, tokenHandler, usendtScheduler::kjørEnGang, stillingKlient::hentStilling, autorisasjon)
        val svarstatistikkController = SvarstatistikkController(repository)

        lateinit var rapidIsAlive: () -> Boolean
        val rapidsConnection = RapidApplication.create(System.getenv(), configure = { _, kafkarapid ->
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
            usendtScheduler,
            svarService,
            rapidsConnection,
            tokenHandler
        ).start()

    } catch (exception: Exception) {
        log("main()").error("Noe galt skjedde", exception)
    }
}


data class Rollekeys(
    val jobbsokerrettetGruppe: String,
    val arbeidsgiverrettetGruppe: String,
    val utviklerGruppe: String
)

fun initierRollekeys() : Rollekeys {
    val jobbsokerrettetGruppe: String = System.getenv("REKRUTTERINGSBISTAND_JOBBSOKERRETTET")
        ?: throw RuntimeException("Miljøvariabel 'REKRUTTERINGSBISTAND_JOBBSOKERRETTET' er ikke satt")

    val arbeidsgiverrettetGruppe: String = System.getenv("REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET")
        ?: throw RuntimeException("Miljøvariabel 'REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET' er ikke satt")

    val utviklerGruppe: String = System.getenv("REKRUTTERINGSBISTAND_UTVIKLER")
        ?: throw RuntimeException("Miljøvariabel 'REKRUTTERINGSBISTAND_UTVIKLER' er ikke satt")

    return Rollekeys(
        jobbsokerrettetGruppe = jobbsokerrettetGruppe,
        arbeidsgiverrettetGruppe = arbeidsgiverrettetGruppe,
        utviklerGruppe = utviklerGruppe
    )
}
