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
        val kandidathendelseJson = packet["kandidathendelse"]
        val aktørId: String = kandidathendelseJson["aktørId"].textValue()
        val stillingsId: UUID = UUID.fromString(kandidathendelseJson["stillingsId"].textValue())
        val tidspunkt: String = kandidathendelseJson["tidspunkt"].textValue()

        val forespørsel = repository.hentSisteForespørselForKandidatOgStilling(aktørId, stillingsId)

        // TODO Mads: Bør vi virkelig logge hele kandidathendelseJson?

        if (forespørsel == null) {
            val melding = """
                Mottok melding om at CV har blitt delt med arbeidsgiver
                til tross for at kandidaten aldri har blitt forespurt om deling av CV: $kandidathendelseJson
            """.trimIndent()
            log.error(melding)
        } else {
            if (!forespørsel.harSvartJa()) {
                val melding = """
                    Mottok melding om at CV har blitt delt med arbeidsgiver
                    til tross for at kandidaten ikke har svart ja til deling av CV: $kandidathendelseJson
                """.trimIndent()
                log.error(melding)
            }

            val meldingJson = """{"type":"CV_DELT","detaljer":"","tidspunkt":$tidspunkt}"""

            val melding = ProducerRecord(topic, forespørsel.forespørselId.toString(), meldingJson)
            statusOppdateringProducer.send(melding)
        }
    }
}
