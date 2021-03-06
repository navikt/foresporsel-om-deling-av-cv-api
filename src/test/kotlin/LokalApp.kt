import mottasvar.SvarService
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.veilarbaktivitet.avro.DelingAvCvRespons
import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.ForesporselOmDelingAvCv
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import sendforespørsel.ForespørselService
import sendforespørsel.UsendtScheduler
import setup.TestDatabase
import setup.hentToken
import setup.mockConsumer
import setup.mockProducer
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
    producer: Producer<String, ForesporselOmDelingAvCv> = mockProducer(),
    hentStilling: (UUID) -> Stilling? = hentStillingMock,
    forespørselService: ForespørselService = ForespørselService(
        producer,
        repository,
        hentStilling
    ),
    consumer: Consumer<String, DelingAvCvRespons> = mockConsumer()
): App {
    val usendtScheduler = UsendtScheduler(database.dataSource, forespørselService::sendUsendte)
    val forespørselController = ForespørselController(repository, usendtScheduler::kjørEnGang, hentStilling)
    val svarstatistikkController = SvarstatistikkController(repository)

    val issuerProperties = listOf(
        IssuerProperties(
            URL("http://localhost:18300/default/.well-known/openid-configuration"),
            listOf("default"),
            "azuread"
        )
    )

    val svarService = SvarService(consumer, repository)

    val app = App(
        forespørselController,
        svarstatistikkController,
        issuerProperties,
        usendtScheduler,
        svarService
    )

    app.start()

    return app
}
