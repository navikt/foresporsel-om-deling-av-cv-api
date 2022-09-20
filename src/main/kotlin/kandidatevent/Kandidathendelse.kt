package kandidatevent

import Forespørsel
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import utils.log


interface Hendelsestype {
    val eventNavn: String

    companion object {
        private val hendelsestyper = listOf(
            CvDeltViaRekrutteringsBistand(),
            KandidatlisteLukketIngenFikkJobben(),
            KandidatlisteLukketNoenAndreFikkJobben()
        )
        fun valueOf(typeSomString: String) = hendelsestyper.first { it.erAvType(typeSomString) }
        fun eventNavn() = hendelsestyper.map(Hendelsestype::eventNavn)
    }

    fun erAvType(typeSomString: String): Boolean

    fun sendTilAktivitetsplan(
        utførtAvNavIdent: String,
        tidspunkt: String,
        forespørsel: Forespørsel?,
        statusOppdateringProducer: Producer<String, String>
    )
}

interface FikkIkkeJobbenHendelse: Hendelsestype {
    override fun sendTilAktivitetsplan(
        utførtAvNavIdent: String,
        tidspunkt: String,
        forespørsel: Forespørsel?,
        statusOppdateringProducer: Producer<String, String>
    ) {
        if (forespørsel?.harSvartJa() == true) {
            sendMeldingOgLogg(
                forespørsel,
                meldingJson("IKKE_FATT_JOBBEN", detaljer(), utførtAvNavIdent, tidspunkt),
                statusOppdateringProducer
            )
        }
    }
    fun detaljer(): String
}

class KandidatlisteLukketNoenAndreFikkJobben : FikkIkkeJobbenHendelse {
    override val eventNavn = "kandidat.kandidatliste-lukket-noen-andre-fikk-jobben"
    override fun erAvType(typeSomString: String) = "KANDIDATLISTE_LUKKET_NOEN_ANDRE_FIKK_JOBBEN" == typeSomString
    override fun detaljer() = "KANDIDATLISTE_LUKKET_NOEN_ANDRE_FIKK_JOBBEN"
}

class KandidatlisteLukketIngenFikkJobben : FikkIkkeJobbenHendelse {
    override val eventNavn = "kandidat.kandidatliste-lukket-ingen-fikk-jobben"
    override fun erAvType(typeSomString: String) = "KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN" == typeSomString
    override fun detaljer() = "KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN"
}

class CvDeltViaRekrutteringsBistand : Hendelsestype {
    override val eventNavn = "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand"
    override fun erAvType(typeSomString: String) = "CV_DELT_VIA_REKRUTTERINGSBISTAND" == typeSomString
    override fun sendTilAktivitetsplan(
        utførtAvNavIdent: String,
        tidspunkt: String,
        forespørsel: Forespørsel?,
        statusOppdateringProducer: Producer<String, String>
    ) {
        if (forespørsel == null) {
            throw HendelsesFeil(
                """
                    Mottok melding om at CV har blitt delt med arbeidsgiver
                    til tross for at kandidaten ikke har blitt forespurt om deling av CV.
                """.trimIndent()
            )
        }
        sendMeldingOgLogg(
            forespørsel,
            meldingJson("CV_DELT", "", utførtAvNavIdent, tidspunkt),
            statusOppdateringProducer
        )
        if (!forespørsel.harSvartJa()) {
            throw HendelsesFeil(
                """
                    Mottok melding om at CV har blitt delt med arbeidsgiver
                    til tross for at kandidaten ikke har svart ja til deling av CV.
                """.trimIndent()
            )
        }
    }
}

private fun meldingJson(type: String, detaljer: String, utførtAvNavIdent: String, tidspunkt: String) =
    """{"type":"$type","detaljer":"$detaljer","utførtAvNavIdent":"$utførtAvNavIdent","tidspunkt":"$tidspunkt"}"""

val topic = "pto.rekrutteringsbistand-statusoppdatering-v1"

private fun sendMeldingOgLogg(
    forespørsel: Forespørsel,
    meldingJson: String,
    statusOppdateringProducer: Producer<String, String>
) {
    val melding = ProducerRecord(topic, forespørsel.forespørselId.toString(), meldingJson)
    statusOppdateringProducer.send(melding)
    log("sendMeldingOgLogg").info("Har sendt melding til aktivitetsplanen på topic $topic ")
}