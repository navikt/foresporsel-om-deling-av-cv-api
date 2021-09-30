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
import java.net.URL

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
    forespørselService: ForespørselService = ForespørselService(
        producer,
        repository,
        hentStillingMock
    ),
    consumer: Consumer<String, DelingAvCvRespons> = mockConsumer(),
): App {
    val usendtScheduler = UsendtScheduler(database.dataSource, forespørselService::sendUsendte)
    val controller = Controller(repository, usendtScheduler::kjørEnGang)

    val issuerProperties = IssuerProperties(
        URL("http://localhost:18300/default/.well-known/openid-configuration"),
        listOf("default"),
        "isso-idtoken"
    )

    val svarService = SvarService(consumer, repository)

    val app = App(
        controller,
        issuerProperties,
        usendtScheduler,
        svarService
    )

    app.start()

    return app
}
