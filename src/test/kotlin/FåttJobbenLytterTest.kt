import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.mockito.Mockito
import org.slf4j.Logger
import setup.TestDatabase
import setup.mockProducerJson
import utils.objectMapper
import java.time.LocalDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FåttJobbenLytterTest {

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
    fun `Når vi mottar RegistrertFåttJobben-melding skal vi sende melding til Aktivitetsplanen`() {
        val forespørsel = lagreForespørsel(aktørId = "dummyAktørId", svarFraBruker = true)
        val tidspunkt = "2023-04-27T08:57:56.955+02:00"
        val navIdent = "dummyIdent"
        val fåttJobbenMelding = registrertFåttJobbenMelding(
            stillingsId = forespørsel.stillingsId,
            aktørId = forespørsel.aktørId,
            navIdent = navIdent,
            tidspunkt = tidspunkt
        )

        testRapid.sendTestMessage(fåttJobbenMelding)

        val meldingerTilAktivitetsplanen = mockProducer.history()
        assertThat(meldingerTilAktivitetsplanen.size).isOne
        assertThat(meldingerTilAktivitetsplanen[0].key()).isEqualTo(forespørsel.forespørselId.toString())
        val meldingBody = objectMapper.readTree(meldingerTilAktivitetsplanen[0].value())
        assertThat(meldingBody.size()).isEqualTo(4)
        assertThat(meldingBody["type"].asText()).isEqualTo("FÅTT_JOBBEN")
        assertThat(meldingBody["detaljer"].asText()).isEqualTo("")
        assertThat(meldingBody["utførtAvNavIdent"].asText()).isEqualTo(navIdent)
        assertThat(meldingBody["tidspunkt"].asText()).isEqualTo("2023-04-27T08:57:56.955+02:00")
    }

    @Test
    fun `Skal ikke sende melding til Aktivitetsplanen for kandidater som ikke fikk jobben`() {
        TODO("Implementer")
    }

    @Test
    fun `Skal ikke sende melding til Aktivitetsplanen hvis kandidaten svarte nei til deling av CV`() {
        val forespørsel = lagreForespørsel(aktørId = "dummyAktørId", svarFraBruker = false)
        val fåttJobbenMelding = registrertFåttJobbenMelding(
            stillingsId = forespørsel.stillingsId,
            aktørId = forespørsel.aktørId
        )

        testRapid.sendTestMessage(fåttJobbenMelding)

        val meldingerTilAktivitetsplanen = mockProducer.history()
        assertThat(meldingerTilAktivitetsplanen).isEmpty()
    }

    @Test
    fun `Skal ikke sende melding til Aktivitetsplanen hvis kandidaten aldri svarte på forespørsle om deling av CV`() {
        val forespørsel = lagreForespørselUtenSvar(aktørId = "dummyAktørId")
        val fåttJobbenMelding = registrertFåttJobbenMelding(
            stillingsId = forespørsel.stillingsId,
            aktørId = forespørsel.aktørId,
        )

        testRapid.sendTestMessage(fåttJobbenMelding)

        val meldingerTilAktivitetsplanen = mockProducer.history()
        assertThat(meldingerTilAktivitetsplanen).isEmpty()
    }

    @Test
    fun `Skal ikke sende melding til Aktivitetsplanen hvis kandidaten aldri ble spurt om deling av CV`() {
        val fåttJobbenMelding = registrertFåttJobbenMelding(
            stillingsId = UUID.randomUUID(),
            aktørId = "dummy",
        )

        testRapid.sendTestMessage(fåttJobbenMelding)

        val meldingerTilAktivitetsplanen = mockProducer.history()
        assertThat(meldingerTilAktivitetsplanen).isEmpty()
    }

    @Test
    fun `Når hendelse med slutt_av_hendelseskjede satt til true skal ikke noe sendes`() {
        TODO("Implementer")
    }

    @Test
    fun `Når hendelsen kommer skal den republiseres med slutt_av_hendelseskjede satt til true`() {
        TODO("Implementer")
    }

    @Test
    fun `Skal ikke sende melding når man har sendt melding for samme kandidat og stilling det siste minuttet`() {
        TODO("Implementer")
    }

    @Test
    fun `Skal sende melding når man har sendt melding for samme kandidat og stilling for mer enn ett minutt siden`() {
        TODO("Implementer")
    }

    private fun lagreForespørsel(
        aktørId: String,
        svarFraBruker: Boolean,
        stillingsId: UUID = UUID.randomUUID()
    ): Forespørsel {
        val forespørsel = enForespørsel(
            aktørId = aktørId,
            deltStatus = DeltStatus.SENDT,
            stillingsId = stillingsId,
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

    private fun lagreForespørselUtenSvar(
        aktørId: String,
        stillingsId: UUID = UUID.randomUUID()
    ): Forespørsel {
        val forespørsel = enForespørsel(
            aktørId = aktørId,
            deltStatus = DeltStatus.SENDT,
            stillingsId = stillingsId,
            forespørselId = UUID.randomUUID(),
            svar = null
        )

        database.lagreBatch(listOf(forespørsel))
        return forespørsel
    }

    private fun registrertFåttJobbenMelding(
        stillingsId: UUID,
        aktørId: String,
        navIdent: String = "dummy",
        tidspunkt: String = "2023-04-27T08:57:56.955+02:00",
        sluttAvHendelseskjede: Boolean = true
    ) = """
        {
          "aktørId": "$aktørId",
          "organisasjonsnummer": "312113341",
          "kandidatlisteId": "f8157e9c-b4d2-4e93-bd80-1beba22019e8",
          "tidspunkt": "$tidspunkt",
          "stillingsId": "$stillingsId",
          "utførtAvNavIdent": "$navIdent",
          "utførtAvNavKontorKode": "666",
          "synligKandidat": true,
          "inkludering": {
            "harHullICv": true,
            "alder": 61,
            "tilretteleggingsbehov": [],
            "innsatsbehov": "BATT",
            "hovedmål": "SKAFFEA"
          },
          "@event_name": "kandidat_v2.RegistrertFåttJobben",
          "system_participating_services": [
            {
              "id": "97f8acaa-28c8-437b-8ab8-1d5b584f0cbd",
              "time": "2023-04-27T08:58:00.004275019",
              "service": "rekrutteringsbistand-kandidat-api",
              "instance": "rekrutteringsbistand-kandidat-api-pod",
              "image": "etFantastiskImage"
            }
          ]
          ${if (!sluttAvHendelseskjede) "" else """, "@slutt_av_hendelseskjede": $sluttAvHendelseskjede"""}
        }
    """.trimIndent()
}