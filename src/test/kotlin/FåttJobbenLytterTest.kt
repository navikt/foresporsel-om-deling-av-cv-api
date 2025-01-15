import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.slf4j.Logger
import setup.TestDatabase
import setup.mockProducerJson
import utils.objectMapper
import java.time.LocalDateTime
import java.util.*

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
    fun `Når vi mottar RegistrertFåttJobben-hendelse skal vi sende melding til Aktivitetsplanen`() {
        val forespørsel = lagreForespørsel(aktørId = "dummyAktørId", svarFraBruker = true)
        val tidspunkt = "2020-01-01T08:00:00.000+02:00"
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
        assertThat(meldingBody["type"].asText()).isEqualTo("FATT_JOBBEN")
        assertThat(meldingBody["detaljer"].asText()).isEqualTo("")
        assertThat(meldingBody["utførtAvNavIdent"].asText()).isEqualTo(navIdent)
        assertThat(meldingBody["tidspunkt"].asText()).isEqualTo("2020-01-01T08:00:00.000+02:00")
    }

    @Test
    fun `Skal ikke sende melding til Aktivitetsplanen for kandidater som ikke fikk jobben`() {
        val forespørselTilKandidatSomFikkJobben = lagreForespørsel(aktørId = "aktørId1", svarFraBruker = true)
        val forespørselTilKandidatsomIkkeFikkJobben =  lagreForespørsel(aktørId = "aktørId2", svarFraBruker = true)
        val tidspunkt = "2020-01-01T08:00:00.000+02:00"
        val navIdent = "dummyIdent"
        val fåttJobbenMelding = registrertFåttJobbenMelding(
            stillingsId = forespørselTilKandidatSomFikkJobben.stillingsId,
            aktørId = forespørselTilKandidatSomFikkJobben.aktørId,
            navIdent = navIdent,
            tidspunkt = tidspunkt
        )
        assertThat(forespørselTilKandidatSomFikkJobben.forespørselId).isNotEqualTo(forespørselTilKandidatsomIkkeFikkJobben.forespørselId)

        testRapid.sendTestMessage(fåttJobbenMelding)

        val meldingerTilAktivitetsplanen = mockProducer.history()
        assertThat(meldingerTilAktivitetsplanen.size).isOne
        assertThat(meldingerTilAktivitetsplanen[0].key()).isEqualTo(forespørselTilKandidatSomFikkJobben.forespørselId.toString())
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
    fun `Skal ikke behandle hendelse med slutt_av_hendelseskjede satt til true`() {
        val fåttJobbenMelding = registrertFåttJobbenMelding(sluttAvHendelseskjede = true)

        testRapid.sendTestMessage(fåttJobbenMelding)

        assertThat(mockProducer.history().size).isZero
        assertThat(testRapid.inspektør.size).isZero
        verify(log, never()).error(any())
    }

    @Test
    fun `Når hendelsen kommer skal den republiseres med slutt_av_hendelseskjede satt til true`() {
        val forespørsel = lagreForespørsel(aktørId = "dummyAktørId", svarFraBruker = false)
        val fåttJobbenMelding = registrertFåttJobbenMelding(
            stillingsId = forespørsel.stillingsId,
            aktørId = forespørsel.aktørId
        )

        testRapid.sendTestMessage(fåttJobbenMelding)

        assertThat(testRapid.inspektør.size).isEqualTo(1)
        assertThat(testRapid.inspektør.message(0)["@slutt_av_hendelseskjede"].asBoolean()).isTrue
    }

    @Test
    fun `Skal ikke sende melding når man har sendt melding for samme kandidat og stilling tidligere`() {
        val forespørsel = lagreForespørsel(aktørId = "dummyAktørId", svarFraBruker = true)
        val fåttJobbenMelding = registrertFåttJobbenMelding(
            stillingsId = forespørsel.stillingsId,
            aktørId = forespørsel.aktørId
        )

        testRapid.sendTestMessage(fåttJobbenMelding)
        testRapid.sendTestMessage(fåttJobbenMelding)

        assertThat(mockProducer.history().size).isEqualTo(1)
    }

    @Test
    fun `Skal sende melding når man har sendt melding for samme kandidat men for annen stilling`() {
        val aktørId = "aktør1"
        val forespørsel = lagreForespørsel(aktørId = aktørId, stillingsId = UUID.randomUUID(), svarFraBruker = true)
        val forespørselSammeKandidatMenAnnenStilling = lagreForespørsel(aktørId = aktørId, stillingsId = UUID.randomUUID(), svarFraBruker = true)
        val fåttJobbenMelding1 = registrertFåttJobbenMelding(
            stillingsId = forespørsel.stillingsId,
            aktørId = forespørsel.aktørId
        )
        val fåttJobbenMelding2 = registrertFåttJobbenMelding(
            stillingsId = forespørselSammeKandidatMenAnnenStilling.stillingsId,
            aktørId = forespørselSammeKandidatMenAnnenStilling.aktørId
        )

        testRapid.sendTestMessage(fåttJobbenMelding1)
        testRapid.sendTestMessage(fåttJobbenMelding2)

        assertThat(mockProducer.history().size).isEqualTo(2)
    }

    @Test
    fun `Skal sende melding når man har sendt melding for annen kandidat på samme stilling`() {
        val stillingsId = UUID.randomUUID()
        val forespørsel = lagreForespørsel(aktørId = "1", stillingsId = stillingsId, svarFraBruker = true)
        val forespørselAnnenKandidatPåSammeStilling = lagreForespørsel(aktørId = "2", stillingsId = stillingsId, svarFraBruker = true)
        val fåttJobbenMelding1 = registrertFåttJobbenMelding(
            stillingsId = forespørsel.stillingsId,
            aktørId = forespørsel.aktørId
        )
        val fåttJobbenMelding2 = registrertFåttJobbenMelding(
            stillingsId = forespørselAnnenKandidatPåSammeStilling.stillingsId,
            aktørId = forespørselAnnenKandidatPåSammeStilling.aktørId
        )

        testRapid.sendTestMessage(fåttJobbenMelding1)
        testRapid.sendTestMessage(fåttJobbenMelding2)

        assertThat(mockProducer.history().size).isEqualTo(2)
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
        stillingsId: UUID = UUID.randomUUID(),
        aktørId: String = "dummy",
        navIdent: String = "dummy",
        tidspunkt: String = "2020-01-01T08:00:00.000+02:00",
        sluttAvHendelseskjede: Boolean = false
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