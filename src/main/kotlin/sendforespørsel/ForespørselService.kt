package sendforespørsel

import Forespørsel
import Repository
import no.nav.rekrutteringsbistand.avro.ForesporselOmDelingAvCv
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import stilling.Stilling
import utils.log
import java.util.*

class ForespørselService(
    private val producer: Producer<String, ForesporselOmDelingAvCv>,
    private val repository: Repository,
    private val hentStilling: (UUID) -> Stilling?
) {
    fun sendUsendte() {
        val usendteForespørsler = repository.hentUsendteForespørsler()
        log.info("Fant ${usendteForespørsler.size} usendte forespørsler")

        usendteForespørsler.associateBy { it.stillingsId }
            .map(hentStillingMedUuid())
            .filterNotNull()
            .forEach { (stilling, usendtForespørsel) ->
                val melding = ProducerRecord(forespørselTopic, usendtForespørsel.aktørId, usendtForespørsel.tilKafkamelding(stilling))

                producer.send(melding) { _, exception ->
                    if (exception == null) {
                        repository.markerForespørselSendt(usendtForespørsel.id)
                    } else {
                        log.error("Det skjedde noe feil under sending til Kafka", exception)
                    }
                }
            }
    }

    private fun hentStillingMedUuid(): (Map.Entry<UUID, Forespørsel>) -> Pair<Stilling, Forespørsel>? = {
        val stilling = hentStilling(it.key)
        if (stilling == null) {
            log.warn("Ignorerer usendt forespørsel med callId ${it.value.callId} fordi stillingen ikke kunne hentes")
        }

        stilling?.to(it.value)
    }
}
