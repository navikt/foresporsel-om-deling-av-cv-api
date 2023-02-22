import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.slf4j.Logger
import setup.TestDatabase
import setup.mockProducerJson
import java.time.LocalDateTime
import java.util.*

class KandidatEventTest {

    private val database = TestDatabase()
    private val testRapid = TestRapid()
    private val mockProducer = mockProducerJson
    private val log = Mockito.mock(Logger::class.java)
    private val eventNameKandidatlisteLukketIngenFikkJobben = "kandidat.kandidatliste-lukket-ingen-fikk-jobben"
    private val eventNameKandidatlisteLukketNoenAndreFikkJobben = "kandidat.kandidatliste-lukket-noen-andre-fikk-jobben"
    private val eventNameCvDeltViaRekrutteringsbistand = "kandidat.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand"
    private val typeKandidatlisteLukketIngenFikkJobben = "KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN"
    private val typeKandidatlisteLukketNoenAndreFikkJobben = "KANDIDATLISTE_LUKKET_NOEN_ANDRE_FIKK_JOBBEN"
    private val typeCvDeltViaRekrutteringsbistand =  "CV_DELT_VIA_REKRUTTERINGSBISTAND"

    var app: App? = null

    @BeforeEach
    fun before() {
        app = startLokalApp(database = database, testRapid = testRapid, jsonProducer = mockProducer, log = log)
    }

    @AfterEach
    fun tearDown() {
        database.slettAlt()
        testRapid.reset()
        mockProducer.clear()
        app?.close()
    }


    @Test
    fun `Når hendelse med slutt_av_hendelseskjede satt til true skal ikke noe sendes`() {
        val forespørsel = lagreForespørsel(svarFraBruker = true)
        publiserCvDeltMeldingPåRapid(forespørsel.aktørId, forespørsel.stillingsId, enNavIdent, sluttAvHendelseskjede = true)
        assertThat(mockProducer.history().size).isZero
        assertThat(testRapid.inspektør.size).isZero
        verify(log, never()).error(any())
    }

    @Test
    fun `Når kandidathendelse kommer skal hendelse republiseres med slutt_av_hendelseskjede satt til true`() {
        val forespørsel = lagreForespørsel(svarFraBruker = true)
        publiserCvDeltMeldingPåRapid(forespørsel.aktørId, forespørsel.stillingsId, enNavIdent)
        assertThat(testRapid.inspektør.size).isEqualTo(1)
        assertThat(testRapid.inspektør.message(0)["@slutt_av_hendelseskjede"].asBoolean()).isTrue
        verify(log, never()).error(any())
    }

    private fun assertAtMeldingErSendtPåTopicTilAktivitetsplanen(
        aktivitetsplanEventName: String,
        kafkaKey: UUID,
        eventTidspunkt: LocalDateTime,
        navIdent: String,
        detaljer: String = ""
    ) {
        val history = mockProducer.history()
        assertThat(history).hasSize(1)
        assertThat(history.first().key()).isEqualTo(kafkaKey.toString())

        val jsonAsString: String = history.first().value()
        val jsonNode: JsonNode = jacksonObjectMapper().readTree(jsonAsString)!!
        assertThat(jsonNode["type"].asText()).isEqualTo(aktivitetsplanEventName)
        assertThat(jsonNode["detaljer"].asText()).isEqualTo(detaljer)
        assertThat(jsonNode["utførtAvNavIdent"].asText()).isEqualTo(navIdent)
        assertThat(jsonNode["tidspunkt"].asLocalDateTime()).isEqualToIgnoringNanos(eventTidspunkt)
    }

    private fun publiserKandidatlisteLukketIngenFikkJobbenMeldingPåRapid(
        aktørId: String,
        stillingsId: UUID,
        utførtAvNavIdent: String = enNavIdent,
        tidspunktForEvent: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime {
        val eventJson = eventJson(
            eventNameKandidatlisteLukketIngenFikkJobben,
            typeKandidatlisteLukketIngenFikkJobben,
            aktørId,
            stillingsId,
            utførtAvNavIdent,
            tidspunktForEvent
        )
        testRapid.sendTestMessage(eventJson)
        return tidspunktForEvent
    }

    private fun publiserKandidatlisteLukketNoenAndreFikkJobbenMeldingPåRapid(
        aktørId: String,
        stillingsId: UUID,
        utførtAvNavIdent: String = enNavIdent,
        tidspunktForEvent: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime {
        val eventJson = eventJson(
            eventNameKandidatlisteLukketNoenAndreFikkJobben,
            typeKandidatlisteLukketNoenAndreFikkJobben,
            aktørId,
            stillingsId,
            utførtAvNavIdent,
            tidspunktForEvent
        )
        testRapid.sendTestMessage(eventJson)
        return tidspunktForEvent
    }

    private fun publiserCvDeltMeldingPåRapid(
        aktørId: String,
        stillingsId: UUID,
        utførtAvNavIdent: String = enNavIdent,
        tidspunktForEvent: LocalDateTime = LocalDateTime.now(),
        sluttAvHendelseskjede: Boolean? = null
    ): LocalDateTime {
        val eventJson = eventJson(
            eventNameCvDeltViaRekrutteringsbistand,
            typeCvDeltViaRekrutteringsbistand,
            aktørId,
            stillingsId,
            utførtAvNavIdent,
            tidspunktForEvent,
            sluttAvHendelseskjede
        )
        testRapid.sendTestMessage(eventJson)
        return tidspunktForEvent
    }

    private fun lagreForespørsel(svarFraBruker: Boolean): Forespørsel {
        val forespørsel = enForespørsel(
            aktørId = "anyAktørID",
            deltStatus = DeltStatus.SENDT,
            stillingsId = UUID.randomUUID(),
            forespørselId = UUID.randomUUID(),
            svar = Svar(
                harSvartJa = svarFraBruker,
                svarTidspunkt = LocalDateTime.now(),
                svartAv = Ident("a", IdentType.NAV_IDENT)
            )
        )

        database.lagreBatch(listOf(forespørsel))
        return forespørsel
    }

    private fun lagreUbesvartForespørsel(): Forespørsel {
        val forespørsel = enForespørsel(
            aktørId = "anyAktørID",
            deltStatus = DeltStatus.SENDT,
            stillingsId = UUID.randomUUID(),
            forespørselId = UUID.randomUUID(),
            svar = null
        )

        database.lagreBatch(listOf(forespørsel))
        return forespørsel
    }

    private fun eventJson(
        eventName: String,
        type: String,
        aktørId: String,
        stillingsId: UUID,
        utførtAvNavIdent: String,
        tidspunkt: LocalDateTime = LocalDateTime.now(),
        sluttAvHendelseskjede: Boolean? = null
    ) =
        """
            {
                "@event_name": "$eventName",
                "kandidathendelse": {
                    "type":"$type",
                    "aktørId":"$aktørId",
                    "stillingsId":"$stillingsId", 
                    "organisasjonsnummer":"913086619",
                    "kandidatlisteId":"8081ef01-b023-4cd8-bd87-b830d9bcf9a4",
                    "utførtAvNavIdent":"$utførtAvNavIdent",
                    "tidspunkt":"$tidspunkt"
                }
                ${if(sluttAvHendelseskjede==null) "" else """, "@slutt_av_hendelseskjede": $sluttAvHendelseskjede"""}
            }
        """.trimIndent()
}
