package kandidatevent

import Forespørsel
import Repository
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class KandidatLytter(
    rapidsConnection: RapidsConnection,
    private val statusOppdateringProducer: Producer<String, String>,
    private val repository: Repository,
    private val log: Logger = LoggerFactory.getLogger(KandidatLytter::class.java)
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
        val tidspunkt: String = packet["kandidathendelse"]["tidspunkt"].textValue()
        val forespørsel: Forespørsel = repository.hentSisteForespørselForKandidatOgStilling(aktørId, stillingsId)
            ?: throw IllegalStateException(
                "Skal alltid finne en forespørsel for en kandidat som skal ha blitt delt med arbeidsgiver. aktørId=$aktørId, stillingsId=$stillingsId"
            )
        if (!forespørsel.harSvartJa()) {
            // Putt hele kandidatheldense json i msg
            val kandidathendelseJson = packet["kandidathendelse"]
            val msg =
                "Mottok melding om at CV har blitt delt med arbeidsgiver, " +
                        "til tross for at kandidaten ikke har svart ja til deling av CV: " +
                        kandidathendelseJson
            log.error(msg)
        }

        val meldingJson = """{"type":"CV_DELT","detaljer":"","tidspunkt":$tidspunkt}"""

        val melding = ProducerRecord(topic, forespørsel.forespørselId.toString(), meldingJson)
        statusOppdateringProducer.send(melding)
    }
}
