package kandidatevent

import Repository
import com.fasterxml.jackson.databind.node.ArrayNode
import no.nav.helse.rapids_rivers.*
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import utils.objectMapper
import java.util.*

class KandidatlisteLukketLytter(
    rapidsConnection: RapidsConnection,
    private val statusOppdateringProducer: Producer<String, String>,
    private val repository: Repository,
    private val log: Logger = LoggerFactory.getLogger(KandidatLytter::class.java)
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "kandidat_v2.LukketKandidatliste")
                it.rejectValue("@slutt_av_hendelseskjede", true)
                it.requireKey("aktørIderFikkJobben", "aktørIderFikkIkkeJobben", "stillingsId", "utførtAvNavIdent", "tidspunkt")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val noenFikkJobben = !(packet["aktørIderFikkJobben"] as ArrayNode).isEmpty
        val aktørIderFikkIkkeJobben = packet["aktørIderFikkIkkeJobben"].map { it.asText() }
        val stillingsId = UUID.fromString(packet["stillingsId"].asText())
        val navIdent = packet["utførtAvNavIdent"].asText()
        val tidspunkt = packet["tidspunkt"].asText()

        aktørIderFikkIkkeJobben.map {
            val sisteForespørsel = repository.hentSisteForespørselForKandidatOgStilling(aktørId = it, stillingsId = stillingsId) ?: return

            KandidatlisteLukket(
                noenAndreFikkJobben = noenFikkJobben,
                forespørselId = sisteForespørsel.forespørselId,
                utførtAvNavIdent = navIdent,
                tidspunkt = tidspunkt
            )
        }
            .map(KandidatlisteLukket::tilMelding)
            .forEach(statusOppdateringProducer::send)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("$problems")
    }

    override fun onSevere(error: MessageProblems.MessageException, context: MessageContext) {
        super.onSevere(error, context)
    }

    class KandidatlisteLukket(
        noenAndreFikkJobben: Boolean,
        val forespørselId: UUID,
        val utførtAvNavIdent: String,
        val tidspunkt: String
    ) {
        val type = "IKKE_FATT_JOBBEN"
        val detaljer = if (noenAndreFikkJobben) {
            "VET_IKKE"
        } else {
            "KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN"
        }

        fun tilMelding() =
            ProducerRecord(topic,forespørselId.toString(), objectMapper.writeValueAsString(this))
    }
}
