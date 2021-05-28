import no.nav.rekrutteringsbistand.avro.ForesporselOmDelingAvCvKafkamelding
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import stilling.Stilling
import utils.log
import java.util.*

// TODO
const val topic = "blabla"

class KafkaService(
    private val producer: Producer<String, ForesporselOmDelingAvCvKafkamelding>,
    private val repository: Repository,
    private val hentStilling: (UUID) -> Stilling
) {

    fun sendUsendteForespørsler() {
        val usendteForespørsler = repository.hentUsendteForespørsler()
        log.info("Fant ${usendteForespørsler.size} usendte forespørsler")
        usendteForespørsler.forEach {

            val stilling = hentStilling(it.stillingsId)
            log.info("Hentet stilling $stilling")

            val melding = ProducerRecord(topic, it.aktørId, it.tilKafkamelding(stilling))
            producer.send(melding)

            repository.markerForespørselSendt(it.id)
        }
    }
}
