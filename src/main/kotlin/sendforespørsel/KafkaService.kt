package sendforespørsel

import Repository
import no.nav.rekrutteringsbistand.avro.ForesporselOmDelingAvCvKafkamelding
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import stilling.Stilling
import utils.log
import java.util.*

const val topic = "foresporsel-om-deling-av-cv"

class KafkaService(
    private val producer: Producer<String, ForesporselOmDelingAvCvKafkamelding>,
    private val repository: Repository,
    private val hentStilling: (UUID) -> Stilling
) {

    fun sendUsendteForespørsler() {
        val usendteForespørsler = repository.hentUsendteForespørsler()
        log.info("Fant ${usendteForespørsler.size} usendte forespørsler")
        usendteForespørsler.associateBy { it.stillingsId }
            .map { hentStilling(it.key) to it.value }
            .forEach { (stilling, usendtForespørsel) ->
                val melding = ProducerRecord(topic, usendtForespørsel.aktørId, usendtForespørsel.tilKafkamelding(stilling))
                producer.send(melding)

                repository.markerForespørselSendt(usendtForespørsel.id)
            }
    }
}
