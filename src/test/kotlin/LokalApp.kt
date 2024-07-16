import auth.Autorisasjon
import auth.TokenCache
import auth.TokenHandler
import auth.obo.KandidatsokApiKlient
import auth.obo.OnBehalfOfTokenClient
import kandidatevent.DelCvMedArbeidsgiverLytter
import kandidatevent.KandidatlisteLukketLytter
import kandidatevent.RegistrertFåttJobbenLytter
import mottasvar.SvarService
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.veilarbaktivitet.avro.DelingAvCvRespons
import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.ForesporselOmDelingAvCv
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import sendforespørsel.ForespørselService
import sendforespørsel.UsendtScheduler
import setup.*
import stilling.Stilling
import java.net.URL
import java.util.*

fun main() {
    val mockOAuth2Server = MockOAuth2Server().apply {
        this.start(port = 18300)
    }

    println("Token for lokal testing: ${hentToken("A123456", mockOAuth2Server)}")

    startLokalApp()
}

fun startLokalApp(
    database: TestDatabase = TestDatabase(),
    repository: Repository = Repository(database.dataSource),
    avroProducer: Producer<String, ForesporselOmDelingAvCv> = mockProducerAvro,
    hentStilling: (UUID) -> Stilling? = hentStillingMock,
    forespørselService: ForespørselService = ForespørselService(
        avroProducer,
        repository,
        hentStilling
    ),
    consumer: Consumer<String, DelingAvCvRespons> = mockConsumer(),
    testRapid: TestRapid = TestRapid(),
    jsonProducer: Producer<String, String> = mockProducerJson,
    log: Logger = LoggerFactory.getLogger("LokalApp")
): App {

    val tokenCache = TokenCache(30L)
    val usendtScheduler = UsendtScheduler(database.dataSource, forespørselService::sendUsendte)

    val tokenHandler = TokenHandler(
        listOf(
            IssuerProperties(
                URL("http://localhost:18300/default/.well-known/openid-configuration"),
                listOf("default"),
                "azuread"
            )
        ),
        Rollekeys("jobbsokerrettetGruppe", "arbeidsgiverrettetGruppe", "utviklerGruppe")
    )
    val issuerProperties = listOf(
        IssuerProperties(
            URL("http://localhost:18300/default/.well-known/openid-configuration"),
            listOf("default"),
            "azuread"
        )
    )
    val kandidatsokApiKlient = KandidatsokApiKlient(
        OnBehalfOfTokenClient(
            testAzureConfig,
            TokenHandler(
                issuerProperties,
                Rollekeys("jobbsokerrettetGruppe", "arbeidsgiverrettetGruppe", "utviklerGruppe")
            ),
            tokenCache
        )
    )
    val autorisasjon = Autorisasjon(kandidatsokApiKlient)

    val forespørselController =
        ForespørselController(repository, tokenHandler, usendtScheduler::kjørEnGang, hentStilling, autorisasjon)
    val svarstatistikkController = SvarstatistikkController(repository)


    val svarService = SvarService(consumer, repository) { true }

    KandidatlisteLukketLytter(testRapid, "topic", jsonProducer, repository, log)
    DelCvMedArbeidsgiverLytter(testRapid, "topic", jsonProducer, repository, log)
    RegistrertFåttJobbenLytter(testRapid, "topic", jsonProducer, repository)

    val app = App(
        forespørselController,
        svarstatistikkController,
        usendtScheduler,
        svarService,
        testRapid,
        tokenHandler
    )

    app.start()

    return app
}
