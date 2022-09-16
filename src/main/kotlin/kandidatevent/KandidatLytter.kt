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
                it.demandAny("@event_name", Hendelsestype.values().map { type -> type.eventName })
                it.interestedIn("kandidathendelse")
            }
        }.register(this)
    }


    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Mottok kandidatevent fra rapid")
        val kandidathendelseJson = packet["kandidathendelse"]
        val aktørId: String = kandidathendelseJson["aktørId"].textValue()
        val stillingsId: UUID = UUID.fromString(kandidathendelseJson["stillingsId"].textValue())
        val tidspunkt: String = kandidathendelseJson["tidspunkt"].textValue()
        val utførtAvNavIdent: String = kandidathendelseJson["utførtAvNavIdent"].textValue()
        val forespørsel = repository.hentSisteForespørselForKandidatOgStilling(aktørId, stillingsId)
        val type: Hendelsestype = Hendelsestype.valueOf(kandidathendelseJson["type"].textValue())
        val ikkeFåttJobbenType =
            type == Hendelsestype.KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN || type == Hendelsestype.KANDIDATLISTE_LUKKET_NOEN_ANDRE_FIKK_JOBBEN
        val detaljer: String = if (ikkeFåttJobbenType) type.name else ""
        val meldingJson =
            """{"type":"${type.aktivitetsplanEventName}","detaljer":"$detaljer","utførtAvNavIdent":"$utførtAvNavIdent","tidspunkt":"$tidspunkt"}"""


        when {
            forespørsel != null && forespørsel.harSvartJa() && ikkeFåttJobbenType -> sendMeldingOgLogg(
                forespørsel,
                meldingJson
            )

            forespørsel != null && forespørsel.harSvartJa() && type == Hendelsestype.CV_DELT_VIA_REKRUTTERINGSBISTAND -> sendMeldingOgLogg(
                forespørsel,
                meldingJson
            )

            forespørsel != null && type == Hendelsestype.CV_DELT_VIA_REKRUTTERINGSBISTAND -> {
                sendMeldingOgLogg(forespørsel, meldingJson)
                log.error(harIkkeSvartJa(aktørId, stillingsId))
            }

            forespørsel == null && type == Hendelsestype.CV_DELT_VIA_REKRUTTERINGSBISTAND -> log.error(
                forespørselErNull(
                    aktørId,
                    stillingsId
                )
            )

            else -> {}
        }
    }

    private fun sendMeldingOgLogg(forespørsel: Forespørsel, meldingJson: String) {
        val melding = ProducerRecord(topic, forespørsel.forespørselId.toString(), meldingJson)
        statusOppdateringProducer.send(melding)
        log.info("Har sendt melding til aktivitetsplanen på topic $topic ")
    }

    private fun forespørselErNull(aktørId: String, stillingsId: UUID): String =
        """
            Mottok melding om at CV har blitt delt med arbeidsgiver
            til tross for at kandidaten ikke har blitt forespurt om deling av CV. aktørId=$aktørId, stillingsId=$stillingsId
        """.trimIndent()

    private fun harIkkeSvartJa(aktørId: String, stillingsId: UUID): String =
        """
            Mottok melding om at CV har blitt delt med arbeidsgiver
            til tross for at kandidaten ikke har svart ja til deling av CV. aktørId=$aktørId, stillingsId=$stillingsId
        """.trimIndent()

    enum class Hendelsestype(val eventName: String, val aktivitetsplanEventName: String) {
        CV_DELT_VIA_REKRUTTERINGSBISTAND("kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand", "CV_DELT"),
        KANDIDATLISTE_LUKKET_NOEN_ANDRE_FIKK_JOBBEN("kandidat.kandidatliste-lukket-noen-andre-fikk-jobben", "IKKE_FATT_JOBBEN"),
        KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN("kandidat.kandidatliste-lukket-ingen-fikk-jobben", "IKKE_FATT_JOBBEN");
    }
}
