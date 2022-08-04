import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import setup.TestDatabase
import setup.mockProducerJson
import java.time.LocalDateTime
import java.util.*

class KandidatEventTest {

    private val database = TestDatabase()
    private val testRapid = TestRapid()
    private val mockProducer = mockProducerJson

    @AfterEach
    fun tearDown() {
        database.slettAlt()
        testRapid.reset()
        mockProducer.clear()
    }

    @Test
    fun `Når CV er delt med arbeidsgiver og kandidaten har svart Ja på forespørsel skal melding sendes til Aktivitetsplanen`() {
        startTestApp().use {
            val forespørsel = lagreForespørsel(svarFraBruker = true)
            val eventTidspunkt = publiserKandidathendelsePåRapid(forespørsel.aktørId, forespørsel.stillingsId)
            assertAtMeldingErSendtPåTopicTilAktivitetsplanen(forespørsel, eventTidspunkt)
        }
    }

    /**
     * Gitt kandidat aldri forespurt om deling av CV
     * Når melding om at CV leses fra rapid'en
     * Så skal vi logge error (som beskriver at feilen ligger i annen applikasjon) uten å kaste exception
     *
     * Gitt kandidat forespurt men ikke svart Ja
     * Når melding om at CV leses fra rapid'en
     * Så skal vi logge error (som beskriver at feilen ligger i annen applikasjon)
     *  og sende melding til Aktivitetsplanen
     */

    @Test
    fun `Kandidat har ikke gitt samtykke til deling av CV`() {
        startTestApp().use {
            val forespørsel = lagreForespørsel(svarFraBruker = false)
            val eventTidspunkt = publiserKandidathendelsePåRapid(forespørsel.aktørId, forespørsel.stillingsId)


            assertExceptionNårEventMottasPåRapid(forespørsel.aktørId, forespørsel.stillingsId)
        }
    }

    @Test
    fun `Kast feil når CV har blitt delt med arbeidsgiver selv om kandidaten ikke har blitt forespørsel`() {
        startTestApp().use {
            val forespørselSomIkkeFinnesIDatabasen = enForespørsel()
            assertExceptionNårEventMottasPåRapid(forespørselSomIkkeFinnesIDatabasen.aktørId, forespørselSomIkkeFinnesIDatabasen.stillingsId)
        }
    }

    private fun assertExceptionNårEventMottasPåRapid(aktørId: String, stillingsId: UUID) {
        val eventJson = eventJson(aktørId, stillingsId)
        assertThrows<IllegalStateException> {
            testRapid.sendTestMessage(eventJson)
        }
    }

    private fun assertAtMeldingErSendtPåTopicTilAktivitetsplanen(forespørsel: Forespørsel, eventTidspunkt: LocalDateTime) {
        val history = mockProducer.history()
        assertThat(history).hasSize(1)
        assertThat(history.first().key()).isEqualTo(forespørsel.forespørselId.toString())

        assertThat(
            history.first().value()
        ).isEqualTo("""{"type":"CV_DELT","detaljer":"","tidspunkt":$eventTidspunkt}""")
    }

    private fun publiserKandidathendelsePåRapid(
        aktørId: String,
        stillingsId: UUID,
        tidspunktForEvent: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime {
        val eventJson = eventJson(aktørId, stillingsId, tidspunktForEvent)
        testRapid.sendTestMessage(eventJson)
        return tidspunktForEvent
    }

    private fun lagreForespørsel(svarFraBruker: Boolean): Forespørsel {
        val forespørsel = enForespørsel(
            aktørId = tilfeldigString(lengde = 10),
            deltStatus = DeltStatus.SENDT,
            stillingsId = UUID.randomUUID(),
            forespørselId = UUID.randomUUID(),
            svar = Svar(harSvartJa = svarFraBruker, svarTidspunkt = LocalDateTime.now(), svartAv = Ident("a", IdentType.NAV_IDENT))
        )

        database.lagreBatch(listOf(forespørsel))
        return forespørsel
    }

    private fun eventJson(aktørId: String, stillingsId: UUID, tidspunkt: LocalDateTime = LocalDateTime.now()) =
        """
            {
                "@event_name": "kandidat.dummy2.cv-delt-med-arbeidsgiver-via-rekrutteringsbistand",
                "kandidathendelse": {
                    "type":"CV_DELT_VIA_REKRUTTERINGSBISTAND",
                    "aktørId":"$aktørId",
                    "stillingsId":"$stillingsId", 
                    "organisasjonsnummer":"913086619",
                    "kandidatlisteId":"8081ef01-b023-4cd8-bd87-b830d9bcf9a4",
                    "tidspunkt":"$tidspunkt"
                }
            }
        """.trimIndent()

    private fun tilfeldigString(lengde: Int = 10) = (1..lengde).map { ('A'..'Å').random() }.joinToString()

    private fun startTestApp() = startLokalApp(database = database, testRapid = testRapid, jsonProducer = mockProducer)
}
