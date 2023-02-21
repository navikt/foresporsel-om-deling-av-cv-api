import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.apache.kafka.clients.producer.ProducerRecord
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

class KandidathendelserTest {

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
    fun `Når vi mottar KandidatlisteLukket-melding skal vi sende meldinger til aktivitetsplanen for kandidater som ikke fikk jobben`() {
        val forespørsel1 = lagreForespørsel(aktørId = "aktør1", svarFraBruker = true)
        val forespørsel2 = lagreForespørsel(aktørId = "aktør2", svarFraBruker = true)
        val kandidatlisteLukketMelding = kandidatlisteLukket(aktørIderFikIkkeJobben = listOf(forespørsel1.aktørId, forespørsel2.aktørId), stillingsId = UUID.randomUUID(), navIdent = "enNavIdent")

        testRapid.sendTestMessage(kandidatlisteLukketMelding)

        val inspektør = mockProducer.history()
        assertThat(inspektør.size).isEqualTo(2)

        val kafkaKeys = inspektør.map(ProducerRecord<String, String>::key)
        assertThat(kafkaKeys).containsExactlyInAnyOrder(
            forespørsel1.forespørselId.toString(),
            forespørsel2.forespørselId.toString()
        )

        inspektør
            .map(ProducerRecord<String, String>::value)
            .map<String?, JsonNode?>(objectMapper::readTree)
            .map { it!! }
            .forEach { message ->
                assertThat(message["type"].asText()).isEqualTo("IKKE_FATT_JOBBEN")
                assertThat(message["detaljer"].asText()).isEqualTo("KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN")
                assertThat(message["utførtAvNavIdent"].asText()).isEqualTo("enNavIdent")
                assertThat(message["tidspunkt"].asText()).isEqualTo("2023-02-21T08:38:01.053+01:00")
            }
    }





    //{"type":"IKKE_FATT_JOBBEN","detaljer":"KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN","utførtAvNavIdent":"A100001","tidspunkt":"2023-02-21T14:26:29.775384"}

    private fun lagreForespørsel(aktørId: String, svarFraBruker: Boolean): Forespørsel {
        val forespørsel = enForespørsel(
            aktørId = aktørId,
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

    private fun kandidatlisteLukket(
        aktørIderFikkJobben: List<String> = emptyList(),
        aktørIderFikIkkeJobben: List<String> = emptyList(),
        stillingsId: UUID,
        navIdent: String
    ) = """
        {
          "aktørIderFikkJobben": ${aktørIderFikkJobben.joinToString(separator = ", ", prefix = "[", postfix = "]")},
          "aktørIderFikkIkkeJobben": ${aktørIderFikIkkeJobben.joinToString(separator = ", ", prefix = "[", postfix = "]")},
          "organisasjonsnummer": "312113341",
          "kandidatlisteId": "f3f4a72b-1388-4a1b-b808-ed6336e2c6a4",
          "tidspunkt": "2023-02-21T08:38:01.053+01:00",
          "stillingsId": "$stillingsId",
          "utførtAvNavIdent": "$navIdent",
          "@event_name": "kandidat_v2.LukketKandidatliste",
          "@id": "7fa7ab9a-d016-4ed2-9f9a-d1a1ad7018f1",
          "@opprettet": "2023-02-21T08:39:01.937854240",
          "system_read_count": 0,
        }
    """.trimIndent()
}