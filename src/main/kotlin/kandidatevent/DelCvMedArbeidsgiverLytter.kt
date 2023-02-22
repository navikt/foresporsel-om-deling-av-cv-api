package kandidatevent

import Repository
import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import utils.objectMapper
import utils.toUUID
import java.util.*

class DelCvMedArbeidsgiverLytter(
    rapidsConnection: RapidsConnection,
    private val statusOppdateringProducer: Producer<String, String>,
    private val repository: Repository,
    private val log: Logger = LoggerFactory.getLogger(KandidatLytter::class.java)
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "kandidat_v2.DelCvMedArbeidsgiver")
                it.rejectValue("@slutt_av_hendelseskjede", true)
                it.requireKey("kandidater", "stillingsId", "utførtAvNavIdent", "tidspunkt")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val stillingsId = packet["stillingsId"].asText().toUUID()
        val navIdent = packet["utførtAvNavIdent"].asText()
        val tidspunkt = packet["tidspunkt"].asText()

        packet["kandidater"].fields().asSequence()
            .map(MutableMap.MutableEntry<String, JsonNode>::key)
            .mapNotNull { repository.hentSisteForespørselForKandidatOgStilling(aktørId = it, stillingsId = stillingsId)  }
            .map {
                DelCvMedArbeidsgiver(
                    forespørselId = it.forespørselId,
                    utførtAvNavIdent = navIdent,
                    tidspunkt = tidspunkt
                )
            }
            .mapNotNull(DelCvMedArbeidsgiver::tilMelding)
            .forEach(statusOppdateringProducer::send)
    }

    private class DelCvMedArbeidsgiver(
        private val forespørselId: UUID,
        val utførtAvNavIdent: String,
        val tidspunkt: String?
    ) {
        val detaljer = ""
        val type = "CV_DELT"
        fun tilMelding() =
            ProducerRecord(topic, forespørselId.toString(), objectMapper.writeValueAsString(this))

    }
}