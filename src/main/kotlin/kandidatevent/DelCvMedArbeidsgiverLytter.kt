package kandidatevent

import Repository
import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
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
    private val eksterntTopic: String,
    private val statusOppdateringProducer: Producer<String, String>,
    private val repository: Repository,
    private val log: Logger = LoggerFactory.getLogger(DelCvMedArbeidsgiverLytter::class.java)
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition{
                it.requireValue("@event_name", "kandidat_v2.DelCvMedArbeidsgiver")
                it.forbidValue("@slutt_av_hendelseskjede", true)
            }
            validate {
                it.requireKey("kandidater", "stillingsId", "utførtAvNavIdent", "tidspunkt")
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        val stillingsId = packet["stillingsId"].asText().toUUID()
        val navIdent = packet["utførtAvNavIdent"].asText()
        val tidspunkt = packet["tidspunkt"].asText()

        packet["kandidater"].fields().asSequence()
            .map(MutableMap.MutableEntry<String, JsonNode>::key)
            .mapNotNull {
                val forespørsel =
                    repository.hentSisteForespørselForKandidatOgStilling(aktørId = it, stillingsId = stillingsId)
                if (forespørsel != null) {
                    forespørsel
                } else {
                    log.error("Mottok melding om at CV har blitt delt med arbeidsgiver, men kandidaten har aldri blitt spurt om deling av CV")
                    null
                }
            }
            .onEach {
                if (!it.harSvartJa()) {
                    log.error("Mottok melding om at CV har blitt delt med arbeidsgiver, men kandidaten svarte nei på deling av CV")
                }
            }
            .map {
                DelCvMedArbeidsgiver(
                    forespørselId = it.forespørselId,
                    utførtAvNavIdent = navIdent,
                    tidspunkt = tidspunkt
                )
            }
            .mapNotNull { it.tilMelding(eksterntTopic) }
            .forEach(statusOppdateringProducer::send)

        packet["@slutt_av_hendelseskjede"] = true
        context.publish(packet.toJson())
    }

    private class DelCvMedArbeidsgiver(
        private val forespørselId: UUID,
        val utførtAvNavIdent: String,
        val tidspunkt: String?
    ) {
        val detaljer = ""
        val type = "CV_DELT"
        fun tilMelding(topic: String) =
            ProducerRecord(topic, forespørselId.toString(), objectMapper.writeValueAsString(this))
    }
}