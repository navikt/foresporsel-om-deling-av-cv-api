import no.nav.rekrutteringsbistand.avro.ForesporselOmDelingAvCvKafkamelding
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord

// TODO
const val topic = "blabla"

class KafkaService(
    private val producer: Producer<String, ForesporselOmDelingAvCvKafkamelding>,
    private val repository: Repository
) {

    fun sendUsendteForespørsler() {
        repository.hentUsendteForespørsler().forEach {

            // TODO: Hent stilling fra API
            val stilling = Stilling("blabla")

            val melding = ProducerRecord(topic, it.aktørId, it.tilKafkamelding(stilling))
            producer.send(melding)

            repository.markerForespørselSendt(it.id)
        }
    }
}


// TODO, flytt
data class Stilling(
    val blabla: String
)
