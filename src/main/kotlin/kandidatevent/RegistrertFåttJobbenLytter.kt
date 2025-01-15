package kandidatevent

import Repository
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
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
) : River.PacketListener {

    private val sendteMeldinger = hashSetOf<UUID>()

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "kandidat_v2.RegistrertFåttJobben")
                it.rejectValue("@slutt_av_hendelseskjede", true)
                it.requireKey("stillingsId", "utførtAvNavIdent", "tidspunkt", "aktørId")
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
        val aktørId = packet["aktørId"].asText()

        val forespørsel = repository.hentSisteForespørselForKandidatOgStilling(aktørId, stillingsId)

        if (forespørsel?.harSvartJa() == true) {
            val harAlleredeSendtMelding = sendteMeldinger.contains(forespørsel.forespørselId)

            if (!harAlleredeSendtMelding) {
                val fåttJobben = FåttJobben(forespørsel.forespørselId, navIdent, tidspunkt)
                statusOppdateringProducer.send(fåttJobben.tilMelding(eksterntTopic))
                sendteMeldinger.add(forespørsel.forespørselId)
            }
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
        val type = "FATT_JOBBEN"
        fun tilMelding(topic: String) =
            ProducerRecord(topic, forespørselId.toString(), objectMapper.writeValueAsString(this))
    }
}