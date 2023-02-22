import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
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
        val forespørsel = lagreForespørsel(svarFraBruker = true)
        val cvDeltMelding = cvDeltMelding(aktørIder = listOf(forespørsel.aktørId), stillingsId = forespørsel.stillingsId, navIdent = enNavIdent)

        testRapid.sendTestMessage(cvDeltMelding)

        val inspektør = mockProducer.history()
        assertThat(inspektør.size).isEqualTo(1)
        val melding = inspektør[0]
        assertThat(melding.key()).isEqualTo(forespørsel.forespørselId)
        val meldingBody = objectMapper.readTree(melding.value())
        assertThat(meldingBody.size()).isEqualTo(4)
        assertThat(meldingBody["type"].asText()).isEqualTo("CV_DELT")
        assertThat(meldingBody["detaljer"].asText()).isEqualTo("")
        assertThat(meldingBody["utførtAvNavIdent"].asText()).isEqualTo(enNavIdent)
        assertThat(meldingBody["tidspunkt"].asText()).isEqualTo("2023-02-09T09:45:53.649+01:00")
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

    fun cvDeltMelding(
        aktørIder: List<String> = emptyList(),
        stillingsId: UUID,
        navIdent: String
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
          "@opprettet": "2023-02-09T09:46:01.027221527",
        }
    """.trimIndent()
}