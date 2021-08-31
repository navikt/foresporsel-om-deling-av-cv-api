package sendforespørsel

import Forespørsel
import Repository
import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.ForesporselOmDelingAvCv
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import stilling.Stilling
import utils.log
import java.util.*

const val forespørselTopic = "pto.deling-av-stilling-fra-nav-forespurt-v2"

class ForespørselService(
    private val producer: Producer<String, ForesporselOmDelingAvCv>,
    private val repository: Repository,
    private val hentStilling: (UUID) -> Stilling?
) {
    fun sendUsendte() {
        val usendteForespørsler = repository.hentUsendteForespørsler()
        if (usendteForespørsler.isNotEmpty()) {
            log.info("Fant ${usendteForespørsler.size} usendte forespørsler")
        }

        usendteForespørsler.associateBy { it.stillingsId }
            .map(hentStillingMedUuid())
            .filterNotNull()
            .forEach { (stilling, usendtForespørsel) ->
                val melding = ProducerRecord(forespørselTopic, usendtForespørsel.aktørId, usendtForespørsel.tilKafkamelding(stilling))

                producer.send(melding) { _, exception ->
                    if (exception == null) {
                        repository.markerForespørselSendt(usendtForespørsel.id)
                        log.info("Sendte forespørsel om deling av CV, forespørsel-ID: ${usendtForespørsel.id}, stillings-ID: ${usendtForespørsel.stillingsId}")
                    } else {
                        log.error("Det skjedde noe feil under sending til Kafka", exception)
                    }
                }
            }
    }

    private fun hentStillingMedUuid(): (Map.Entry<UUID, Forespørsel>) -> Pair<Stilling, Forespørsel>? = {
        val stilling = hentStilling(it.key)
        if (stilling == null) {
            log.error("Ignorerer usendt forespørsel med id ${it.value.forespørselId} fordi stillingen ikke kunne hentes")
        }

        stilling?.to(it.value)
    }
}
