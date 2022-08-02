package kandidatevent

import Forespørsel
import Repository
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import utils.log
import java.time.LocalDateTime
import java.util.UUID

class KandidatLytter(
    rapidsConnection: RapidsConnection,
    private val statusOppdateringProducer: Producer<String, String>,
    private val repository: Repository
) : River.PacketListener {

    val topic = "pto.rekrutteringsbistand-statusoppdatering-v1"

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "kandidat.dummy2.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand")
                it.interestedIn("kandidathendelse")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Mottok kandidatevent: $packet")
        val aktørId: String = packet["kandidathendelse"]["aktørId"].textValue()
        val stillingsId: UUID = UUID.fromString(packet["kandidathendelse"]["stillingsId"].textValue())
        val forespørsel: Forespørsel = repository.hentSisteForespørselForKandidatOgStilling(aktørId, stillingsId)
            ?: throw IllegalStateException(
                "Skal alltid finne en forespørsel for en kandidat som skal ha blitt delt med arbeidsgiver. aktørId=$aktørId, stillingsId=$stillingsId"
            )
        if(!forespørsel.harSvartJa()) {
            throw IllegalStateException(
                "Forespørsel skal ikke ha svar nei, da kan vi ikke dele til arebidsgiver"
            )
        }

        val meldingJson = """{"type":"CV_DELT","detaljer":"","tidspunkt":${LocalDateTime.now()}}"""

        val melding = ProducerRecord(topic, forespørsel.forespørselId.toString(), meldingJson)
        statusOppdateringProducer.send(melding)
    }
}
