package kandidatevent

import Repository
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import utils.objectMapper
import utils.toUUID
import java.util.*

class RegistrertFåttJobbenLytter(
    rapidsConnection: RapidsConnection,
    private val eksterntTopic: String,
    private val statusOppdateringProducer: Producer<String, String>,
    private val repository: Repository,
): River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "kandidat_v2.RegistrertFåttJobben")
                it.rejectValue("@slutt_av_hendelseskjede", true)
                it.requireKey("stillingsId", "utførtAvNavIdent", "tidspunkt", "aktørId")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val stillingsId = packet["stillingsId"].asText().toUUID()
        val navIdent = packet["utførtAvNavIdent"].asText()
        val tidspunkt = packet["tidspunkt"].asText()
        val aktørId = packet["aktørId"].asText()

        val forespørsel = repository.hentSisteForespørselForKandidatOgStilling(aktørId, stillingsId)

        if (forespørsel?.harSvartJa() == true) {
            val fåttJobben = FåttJobben(forespørsel.forespørselId, navIdent, tidspunkt)
            statusOppdateringProducer.send(fåttJobben.tilMelding(eksterntTopic))
        }

        packet["@slutt_av_hendelseskjede"] = true
        context.publish(packet.toJson())
    }

    private class FåttJobben(
        private val forespørselId: UUID,
        val utførtAvNavIdent: String,
        val tidspunkt: String
    ) {
        val detaljer = ""
        val type = "FÅTT_JOBBEN"
        fun tilMelding(topic: String) =
            ProducerRecord(topic, forespørselId.toString(), objectMapper.writeValueAsString(this))
    }
}