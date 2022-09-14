import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kandidatevent.KandidatLytter
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mockito
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
    fun `Når CV er delt med arbeidsgiver og kandidaten har svart Ja på forespørsel skal melding sendes til Aktivitetsplanen`() {
        val forespørsel = lagreForespørsel(svarFraBruker = true)
        val eventTidspunkt = publiserCvDeltMeldingPåRapid(forespørsel.aktørId, forespørsel.stillingsId, enNavIdent)
        assertAtMeldingErSendtPåTopicTilAktivitetsplanen(KandidatLytter.Hendelsestype.CV_DELT_VIA_REKRUTTERINGSBISTAND, forespørsel, eventTidspunkt, enNavIdent)
    }

    @Test
    fun `CV er delt med arbeidsgiver på tross av at kandidat ikke har svart ja på forespørsel om deling av CV`() {
        val forespørsel = lagreForespørsel(svarFraBruker = false)

        val eventTidspunkt = publiserCvDeltMeldingPåRapid(forespørsel.aktørId, forespørsel.stillingsId, enNavIdent)

        verify(log).error(startsWith("Mottok melding om at CV har blitt delt med arbeidsgiver"))
        assertAtMeldingErSendtPåTopicTilAktivitetsplanen(KandidatLytter.Hendelsestype.CV_DELT_VIA_REKRUTTERINGSBISTAND, forespørsel, eventTidspunkt, enNavIdent)
    }

    @Test
    fun `CV er delt med arbeidsgiver på tross av at kandidat ikke har blitt forespurt om deling av CV`() {
        val forespørselSomIkkeFinnesIDatabasen = enForespørsel()
        publiserCvDeltMeldingPåRapid(
            forespørselSomIkkeFinnesIDatabasen.aktørId,
            forespørselSomIkkeFinnesIDatabasen.stillingsId
        )
        verify(log).error(startsWith("Mottok melding om at CV har blitt delt med arbeidsgiver"))
        assertThat(mockProducer.history().size).isZero
    }

    @Test
    fun `Skal behandle melding om fikk-ikke-jobben når kandidat svarte ja til deling av CV`() {
        val forespørsel = lagreForespørsel(svarFraBruker = true)
        val eventTidspunkt = publiserCvDeltMeldingPåRapid(forespørsel.aktørId, forespørsel.stillingsId, enNavIdent)
        assertAtMeldingErSendtPåTopicTilAktivitetsplanen(KandidatLytter.Hendelsestype.FIKK_IKKE_JOBBEN, forespørsel, eventTidspunkt, enNavIdent)
    }

    @Test
    fun `Skal ignorere melding om fikk-ikke-jobben når kandidaten svarte nei til deling av CV`() {
        val forespørsel = lagreForespørsel(svarFraBruker = false)
        publiserFikkIkkeJobbenMeldingPåRapid(forespørsel.aktørId, forespørsel.stillingsId, enNavIdent)
        assertThat(mockProducer.history().size).isZero
    }

    @Test
    fun `Skal ignorere melding om fikk-ikke-jobben når kandidat aldri svarte på forespørsel om deling av CV`() {
        val forespørsel = lagreUbesvarForespørsel()
        publiserFikkIkkeJobbenMeldingPåRapid(forespørsel.aktørId, forespørsel.stillingsId, enNavIdent)
        assertThat(mockProducer.history().size).isZero
    }

    private fun assertAtMeldingErSendtPåTopicTilAktivitetsplanen(
        type: KandidatLytter.Hendelsestype,
        forespørsel: Forespørsel,
        eventTidspunkt: LocalDateTime,
        navIdent: String
    ) {
        val history = mockProducer.history()
        assertThat(history).hasSize(1)
        assertThat(history.first().key()).isEqualTo(forespørsel.forespørselId.toString())

        val jsonAsString: String = history.first().value()
        val jsonNode: JsonNode = jacksonObjectMapper().readTree(jsonAsString)!!
        assertThat(jsonNode["type"].asText()).isEqualTo(type.toString())
        assertThat(jsonNode["detaljer"].asText()).isEmpty()
        assertThat(jsonNode["utførtAvNavIdent"].asText()).isEqualTo(navIdent)
        assertThat(jsonNode["tidspunkt"].asLocalDateTime()).isEqualToIgnoringNanos(eventTidspunkt)
    }

    private fun publiserFikkIkkeJobbenMeldingPåRapid(
        aktørId: String,
        stillingsId: UUID,
        utførtAvNavIdent: String = enNavIdent,
        tidspunktForEvent: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime {
        val eventJson = eventJson(KandidatLytter.Hendelsestype.FIKK_IKKE_JOBBEN, aktørId, stillingsId, utførtAvNavIdent, tidspunktForEvent)
        testRapid.sendTestMessage(eventJson)
        return tidspunktForEvent
    }

    private fun publiserCvDeltMeldingPåRapid(
        aktørId: String,
        stillingsId: UUID,
        utførtAvNavIdent: String = enNavIdent,
        tidspunktForEvent: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime {
        val eventJson = eventJson(KandidatLytter.Hendelsestype.CV_DELT_VIA_REKRUTTERINGSBISTAND, aktørId, stillingsId, utførtAvNavIdent, tidspunktForEvent)
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

    private fun lagreUbesvarForespørsel(): Forespørsel {
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
        type: KandidatLytter.Hendelsestype,
        aktørId: String,
        stillingsId: UUID,
        utførtAvNavIdent: String,
        tidspunkt: LocalDateTime = LocalDateTime.now()
    ) =
        """
            {
                "@event_name": "${type.eventName}",
                "kandidathendelse": {
                    "type":"$type",
                    "aktørId":"$aktørId",
                    "stillingsId":"$stillingsId", 
                    "organisasjonsnummer":"913086619",
                    "kandidatlisteId":"8081ef01-b023-4cd8-bd87-b830d9bcf9a4",
                    "utførtAvNavIdent":"$utførtAvNavIdent",
                    "tidspunkt":"$tidspunkt"
                }
            }
        """.trimIndent()
}
