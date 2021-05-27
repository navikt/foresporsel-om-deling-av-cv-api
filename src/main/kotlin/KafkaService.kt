import no.nav.rekrutteringsbistand.avro.ForesporselOmDelingAvCvKafkamelding
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import stilling.Stilling
import java.util.*

// TODO
const val topic = "blabla"

class KafkaService(
    private val producer: Producer<String, ForesporselOmDelingAvCvKafkamelding>,
    private val repository: Repository,
    private val hentStilling: (UUID) -> Stilling
) {

    fun sendUsendteForespørsler() {
        repository.hentUsendteForespørsler().forEach {

            val stilling = hentStilling(it.stillingsId)

            val melding = ProducerRecord(topic, it.aktørId, it.tilKafkamelding(stilling))
            producer.send(melding)

            repository.markerForespørselSendt(it.id)
        }
    }
}
