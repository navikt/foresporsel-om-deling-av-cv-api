package kandidatevent

import Repository
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.apache.kafka.clients.producer.Producer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class KandidatLytter(
    rapidsConnection: RapidsConnection,
    private val statusOppdateringProducer: Producer<String, String>,
    private val repository: Repository,
    private val log: Logger = LoggerFactory.getLogger(KandidatLytter::class.java)
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAny("@event_name", Hendelsestype.eventNavn())
                it.rejectValue("@slutt_av_hendelseskjede", true)
                it.interestedIn("kandidathendelse")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Mottok kandidatevent fra rapid")
        val kandidathendelseJson = packet["kandidathendelse"]
        val aktørId: String = kandidathendelseJson["aktørId"].textValue()
        val stillingsId: UUID = UUID.fromString(kandidathendelseJson["stillingsId"].textValue())
        val type: Hendelsestype = Hendelsestype.valueOf(kandidathendelseJson["type"].textValue())
        try {
            type.sendTilAktivitetsplan(
                utførtAvNavIdent = kandidathendelseJson["utførtAvNavIdent"].textValue(),
                tidspunkt = kandidathendelseJson["tidspunkt"].textValue(),
                forespørsel = repository.hentSisteForespørselForKandidatOgStilling(aktørId, stillingsId),
                statusOppdateringProducer = statusOppdateringProducer
            )
        } catch (e: HendelsesFeil) {
            e.logFeil(log, aktørId, stillingsId)
        }
        packet["@slutt_av_hendelseskjede"] = true
        context.publish(packet.toJson())
    }
}

class HendelsesFeil(message: String): Exception(message) {
    fun logFeil(log: Logger, aktørId: String, stillingsId: UUID) = log.error("$message aktørId=$aktørId, stillingsId=$stillingsId")
}