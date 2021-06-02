import mottasvar.SvarService
import no.nav.rekrutteringsbistand.avro.ForesporselOmDelingAvCv
import no.nav.rekrutteringsbistand.avro.SvarPaForesporselOmDelingAvCv
import no.nav.security.token.support.core.configuration.IssuerProperties
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.Producer
import sendforespørsel.ForespørselService
import sendforespørsel.UsendtScheduler
import setup.TestDatabase
import setup.mockConsumer
import setup.mockProducer
import java.net.URL

fun main() {
    startLokalApp()
}

fun startLokalApp(
    database: TestDatabase = TestDatabase(),
    repository: Repository = Repository(database.dataSource),
    producer: Producer<String, ForesporselOmDelingAvCv> = mockProducer(),
    forespørselService: ForespørselService = ForespørselService(producer, repository) {
        enStilling()
    },
    consumer: Consumer<String, SvarPaForesporselOmDelingAvCv> = mockConsumer(),
): App {
    val controller = Controller(repository)

    val issuerProperties = IssuerProperties(
        URL("http://localhost:18300/default/.well-known/openid-configuration"),
        listOf("default"),
        "isso-idtoken"
    )

    val svarService = SvarService(consumer, repository::oppdaterMedSvar)

    val app = App(
        controller,
        issuerProperties,
        forespørselService,
        UsendtScheduler(database.dataSource,forespørselService::sendUsendte),
        svarService
    )

    app.start()

    return app
}
