import no.nav.rekrutteringsbistand.avro.ForesporselOmDelingAvCvKafkamelding
import no.nav.security.token.support.core.configuration.IssuerProperties
import org.apache.kafka.clients.producer.Producer
import sendforespørsel.KafkaService
import sendforespørsel.UsendtScheduler
import setup.TestDatabase
import setup.mockProducer
import java.net.URL

fun main() {
    startLokalApp()
}

fun startLokalApp(
    database: TestDatabase = TestDatabase(),
    repository: Repository = Repository(database.dataSource),
    producer: Producer<String, ForesporselOmDelingAvCvKafkamelding> = mockProducer(),
    kafkaService: KafkaService = KafkaService(producer, repository) {
        enStilling()
    }
): App {
    val controller = Controller(repository)

    val issuerProperties = IssuerProperties(
        URL("http://localhost:18300/default/.well-known/openid-configuration"),
        listOf("default"),
        "isso-idtoken"
    )

    val app = App(controller, issuerProperties, kafkaService, UsendtScheduler(database.dataSource,kafkaService::sendUsendteForespørsler))

    app.start()

    return app
}
