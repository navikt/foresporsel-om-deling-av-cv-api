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
        assertAtMeldingErSendtPåTopicTilAktivitetsplanen(
            KandidatLytter.Hendelsestype.CV_DELT_VIA_REKRUTTERINGSBISTAND,
            forespørsel.forespørselId,
            eventTidspunkt,
            enNavIdent
        )
    }

    @Test
    fun `CV er delt med arbeidsgiver på tross av at kandidat ikke har svart ja på forespørsel om deling av CV`() {
        val forespørsel = lagreForespørsel(svarFraBruker = false)

        val eventTidspunkt = publiserCvDeltMeldingPåRapid(forespørsel.aktørId, forespørsel.stillingsId, enNavIdent)

        verify(log).error(startsWith("Mottok melding om at CV har blitt delt med arbeidsgiver"))
        assertAtMeldingErSendtPåTopicTilAktivitetsplanen(
            KandidatLytter.Hendelsestype.CV_DELT_VIA_REKRUTTERINGSBISTAND,
            forespørsel.forespørselId,
            eventTidspunkt,
            enNavIdent
        )
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
    fun `Skal behandle melding om kandidatliste-lukket-ingen-fikk-jobben når kandidat svarte ja til deling av CV`() {
        val forespørsel = lagreForespørsel(svarFraBruker = true)
        val eventTidspunkt = publiserKandidatlisteLukketIngenFikkJobbenMeldingPåRapid(
            forespørsel.aktørId,
            forespørsel.stillingsId,
            enNavIdent
        )
        val type = KandidatLytter.Hendelsestype.KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN
        assertAtMeldingErSendtPåTopicTilAktivitetsplanen(
            type,
            forespørsel.forespørselId,
            eventTidspunkt,
            enNavIdent,
            type.name
        )
    }

    @Test
    fun `Skal ignorere melding om kandidatliste-lukket-ingen-fikk-jobben når kandidaten svarte nei til deling av CV`() {
        val forespørsel = lagreForespørsel(svarFraBruker = false)
        publiserKandidatlisteLukketIngenFikkJobbenMeldingPåRapid(
            forespørsel.aktørId,
            forespørsel.stillingsId,
            enNavIdent
        )
        assertThat(mockProducer.history().size).isZero
    }

    @Test
    fun `Skal ignorere melding om kandidatliste-lukket-ingen-fikk-jobben når kandidat aldri svarte på forespørsel om deling av CV`() {
        val forespørsel = lagreUbesvartForespørsel()
        publiserKandidatlisteLukketIngenFikkJobbenMeldingPåRapid(
            forespørsel.aktørId,
            forespørsel.stillingsId,
            enNavIdent
        )
        assertThat(mockProducer.history().size).isZero
    }

    @Test
    fun `Skal ignorere melding om kandidatliste-lukket-ingen-fikk-jobben når kandidat aldri ble spurt om deling av CV`() {
        publiserKandidatlisteLukketIngenFikkJobbenMeldingPåRapid("dummyAktørId", UUID.randomUUID(), enNavIdent)
        assertThat(mockProducer.history().size).isZero
    }

    @Test
    fun `Skal ikke logge feil når vi mottar melding om kandidatliste-lukket-ingen-fikk-jobben når kandidat aldri ble spurt om deling av CV`() {
        publiserKandidatlisteLukketIngenFikkJobbenMeldingPåRapid("dummyAktørId", UUID.randomUUID(), enNavIdent)
        assertThat(mockProducer.history().size).isZero
        verify(log, never()).error(any())
    }

    @Test
    fun `Skal behandle melding om kandidatliste-lukket-noen-andre-fikk-jobben når kandidat svarte ja til deling av CV`() {
        val forespørsel = lagreForespørsel(svarFraBruker = true)
        val eventTidspunkt = publiserKandidatlisteLukketNoenAndreFikkJobbenMeldingPåRapid(
            forespørsel.aktørId,
            forespørsel.stillingsId,
            enNavIdent
        )
        val type = KandidatLytter.Hendelsestype.KANDIDATLISTE_LUKKET_NOEN_ANDRE_FIKK_JOBBEN
        assertAtMeldingErSendtPåTopicTilAktivitetsplanen(
            type,
            forespørsel.forespørselId,
            eventTidspunkt,
            enNavIdent,
            type.name
        )
    }

    @Test
    fun `Skal ignorere melding om kandidatliste-lukket-noen-andre-fikk-jobben når kandidaten svarte nei til deling av CV`() {
        val forespørsel = lagreForespørsel(svarFraBruker = false)
        publiserKandidatlisteLukketNoenAndreFikkJobbenMeldingPåRapid(
            forespørsel.aktørId,
            forespørsel.stillingsId,
            enNavIdent
        )
        assertThat(mockProducer.history().size).isZero
    }

    @Test
    fun `Skal ignorere melding om kandidatliste-lukket-noen-andre-fikk-jobben når kandidat aldri svarte på forespørsel om deling av CV`() {
        val forespørsel = lagreUbesvartForespørsel()
        publiserKandidatlisteLukketNoenAndreFikkJobbenMeldingPåRapid(
            forespørsel.aktørId,
            forespørsel.stillingsId,
            enNavIdent
        )
        assertThat(mockProducer.history().size).isZero
    }

    @Test
    fun `Skal ignorere melding om kandidatliste-lukket-noen-andre-fikk-jobben når kandidat aldri ble spurt om deling av CV`() {
        publiserKandidatlisteLukketNoenAndreFikkJobbenMeldingPåRapid("dummyAktørId", UUID.randomUUID(), enNavIdent)
        assertThat(mockProducer.history().size).isZero
    }

    @Test
    fun `Skal ikke logge feil når vi mottar melding om kandidatliste-lukket-noen-andre-fikk-jobben når kandidat aldri ble spurt om deling av CV`() {
        publiserKandidatlisteLukketNoenAndreFikkJobbenMeldingPåRapid("dummyAktørId", UUID.randomUUID(), enNavIdent)
        assertThat(mockProducer.history().size).isZero
        verify(log, never()).error(any())
    }

    private fun assertAtMeldingErSendtPåTopicTilAktivitetsplanen(
        type: KandidatLytter.Hendelsestype,
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
        assertThat(jsonNode["type"].asText()).isEqualTo(type.aktivitetsplanEventName)
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
            KandidatLytter.Hendelsestype.KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN,
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
            KandidatLytter.Hendelsestype.KANDIDATLISTE_LUKKET_NOEN_ANDRE_FIKK_JOBBEN,
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
        tidspunktForEvent: LocalDateTime = LocalDateTime.now()
    ): LocalDateTime {
        val eventJson = eventJson(
            KandidatLytter.Hendelsestype.CV_DELT_VIA_REKRUTTERINGSBISTAND,
            aktørId,
            stillingsId,
            utførtAvNavIdent,
            tidspunktForEvent
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
