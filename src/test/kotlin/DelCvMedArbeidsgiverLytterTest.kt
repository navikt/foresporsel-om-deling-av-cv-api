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
import utils.objectMapper
import java.time.LocalDateTime
import java.util.*

class DelCvMedArbeidsgiverLytterTest {

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
    fun `Når CV-er er delt med arbeidsgiver skal melding sendes til aktivitetsplanen for kandidater som har svart Ja på forespørsel`() {
        val forespørsel1 = lagreForespørsel(aktørId = "aktørId1", svarFraBruker = true)
        val forespørsel2 = lagreForespørsel(aktørId = "aktørId2", svarFraBruker = true, stillingsId = forespørsel1.stillingsId)
        val cvDeltMelding = cvDeltMelding(aktørIder = listOf(forespørsel1.aktørId, forespørsel2.aktørId), stillingsId = forespørsel1.stillingsId)

        testRapid.sendTestMessage(cvDeltMelding)

        val inspektør = mockProducer.history()
        assertThat(inspektør.size).isEqualTo(2)

        val førsteMelding = inspektør[0]
        assertThat(førsteMelding.key()).isEqualTo(forespørsel1.forespørselId.toString())
        val meldingBody1 = objectMapper.readTree(førsteMelding.value())
        assertThat(meldingBody1.size()).isEqualTo(4)
        assertThat(meldingBody1["type"].asText()).isEqualTo("CV_DELT")
        assertThat(meldingBody1["detaljer"].asText()).isEqualTo("")
        assertThat(meldingBody1["utførtAvNavIdent"].asText()).isEqualTo(enNavIdent)
        assertThat(meldingBody1["tidspunkt"].asText()).isEqualTo("2023-02-09T09:45:53.649+01:00")

        val andreMelding = inspektør[1]
        assertThat(andreMelding.key()).isEqualTo(forespørsel2.forespørselId.toString())
        val meldingBody2 = objectMapper.readTree(andreMelding.value())
        assertThat(meldingBody2.size()).isEqualTo(4)
        assertThat(meldingBody2["type"].asText()).isEqualTo("CV_DELT")
        assertThat(meldingBody2["detaljer"].asText()).isEqualTo("")
        assertThat(meldingBody2["utførtAvNavIdent"].asText()).isEqualTo(enNavIdent)
        assertThat(meldingBody2["tidspunkt"].asText()).isEqualTo("2023-02-09T09:45:53.649+01:00")
    }

    @Test
    fun `Skal sende melding til aktivitetsplanen på tross av at kandidat ikke har svart ja på forespørsel om deling av CV`() {
        val forespørsel = lagreForespørsel(svarFraBruker = false, aktørId = "aktørId")
        val melding = cvDeltMelding(aktørIder = listOf(forespørsel.aktørId), stillingsId = forespørsel.stillingsId)

        testRapid.sendTestMessage(melding)

        verify(log).error(startsWith("Mottok melding om at CV har blitt delt med arbeidsgiver, men kandidaten svarte nei på deling av CV"))
        assertThat(mockProducer.history().size).isEqualTo(1)
    }

    @Test
    fun `Skal ikke sende melding til aktivitetsplanen når kandidat ikke har blitt forespurt om deling av CV`() {
        val melding = cvDeltMelding(aktørIder = listOf("aktør1"))

        testRapid.sendTestMessage(melding)

        verify(log).error(startsWith("Mottok melding om at CV har blitt delt med arbeidsgiver, men kandidaten har aldri blitt spurt om deling av CV"))
        assertThat(mockProducer.history().size).isEqualTo(0)
    }

    private fun lagreForespørsel(aktørId: String, svarFraBruker: Boolean, stillingsId: UUID = UUID.randomUUID()): Forespørsel {
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

    fun cvDeltMelding(
        aktørIder: List<String> = emptyList(),
        stillingsId: UUID = UUID.randomUUID(),
        navIdent: String = enNavIdent
    ) = """
        {
          "stillingstittel": "En fantastisk stilling",
          "organisasjonsnummer": "312113341",
          "kandidatlisteId": "d5b5b4c1-0375-4719-9038-ab31fe27fb40",
          "tidspunkt": "2023-02-09T09:45:53.649+01:00",
          "stillingsId": "$stillingsId",
          "utførtAvNavIdent": "$navIdent",
          "utførtAvNavKontorKode": "0313",
          "utførtAvVeilederFornavn": "F_Z994633",
          "utførtAvVeilederEtternavn": "E_Z994633",
          "arbeidsgiversEpostadresser": [
            "hei@arbeidsgiversdomene.no",
            "enansatt@trygdeetaten.no"
          ],
          "meldingTilArbeidsgiver": "Hei, her er en god kandidat som vil føre til at du kan selge varene dine med høyere avanse!",
          "kandidater": {
            ${aktørIder.map { 
                """
                    "$it": {
                      "harHullICv": false,
                      "alder": 51,
                      "tilretteleggingsbehov": [],
                      "innsatsbehov": "BFORM",
                      "hovedmål": "BEHOLDEA"
                    }
                """.trimIndent()
            }.joinToString(separator = ",")}
          },
          "@event_name": "kandidat_v2.DelCvMedArbeidsgiver",
          "@id": "74b0b8dd-315f-406f-9979-e0bec5bcc5b6",
          "@opprettet": "2023-02-09T09:46:01.027221527"
        }
    """.trimIndent()
}