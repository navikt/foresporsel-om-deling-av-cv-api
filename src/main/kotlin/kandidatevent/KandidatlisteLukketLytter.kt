package kandidatevent

import Repository
import com.fasterxml.jackson.annotation.JsonProperty
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

        aktørIderFikkIkkeJobben
            .mapNotNull { repository.hentSisteForespørselForKandidatOgStilling(aktørId = it, stillingsId = stillingsId)  }
            .filter { it.harSvartJa() }
            .map {
                KandidatlisteLukket(
                    noenAndreFikkJobben = noenFikkJobben,
                    forespørselId = it.forespørselId,
                    utførtAvNavIdent = navIdent,
                    tidspunkt = tidspunkt
                )
            }
            .mapNotNull(KandidatlisteLukket::tilMelding)
            .forEach(statusOppdateringProducer::send)

        packet["@slutt_av_hendelseskjede"] = true
        context.publish(packet.toJson())
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("$problems")
    }

    class KandidatlisteLukket(
        noenAndreFikkJobben: Boolean,
        private val forespørselId: UUID,
        val utførtAvNavIdent: String,
        val tidspunkt: String
    ) {
        val type = "IKKE_FATT_JOBBEN"
        val detaljer = if (noenAndreFikkJobben) {
            "KANDIDATLISTE_LUKKET_NOEN_ANDRE_FIKK_JOBBEN"
        } else {
            "KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN"
        }

        fun tilMelding() =
            ProducerRecord(topic,forespørselId.toString(), objectMapper.writeValueAsString(this))
    }
}
