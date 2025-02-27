import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

class KandidatlisteLukketLytterTest {

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
    fun `Når vi mottar KandidatlisteLukket-melding der ingen fikk jobben skal vi sende meldinger til aktivitetsplanen for kandidater som ikke fikk jobben`() {
        val stillingsId = UUID.randomUUID()
        val forespørsel1 = lagreForespørsel(aktørId = "aktør1", svarFraBruker = true, stillingsId = stillingsId)
        val forespørsel2 = lagreForespørsel(aktørId = "aktør2", svarFraBruker = true, stillingsId = stillingsId)
        val kandidatlisteLukketMelding = kandidatlisteLukket(
            aktørIderFikkIkkeJobben = listOf(forespørsel1.aktørId, forespørsel2.aktørId),
            stillingsId = forespørsel1.stillingsId,
            navIdent = "enNavIdent"
        )

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
                assertThat(message.size()).isEqualTo(4)
                assertThat(message["type"].asText()).isEqualTo("IKKE_FATT_JOBBEN")
                assertThat(message["detaljer"].asText()).isEqualTo("KANDIDATLISTE_LUKKET_INGEN_FIKK_JOBBEN")
                assertThat(message["utførtAvNavIdent"].asText()).isEqualTo("enNavIdent")
                assertThat(message["tidspunkt"].asText()).isEqualTo("2023-02-21T08:38:01.053+01:00")
            }
    }

    @Test
    fun `Når vi mottar KandidatlisteLukket-melding der noen fikk jobben skal vi sende meldinger til aktivitetsplanen for kandidater som ikke fikk jobben`() {
        val stillingsId = UUID.randomUUID()
        val forespørselTilKandidatSomFikkJobben =
            lagreForespørsel(aktørId = "aktør1", svarFraBruker = true, stillingsId = stillingsId)
        val forespørselTilKandidatSomIkkeFikkJobben =
            lagreForespørsel(aktørId = "aktør2", svarFraBruker = true, stillingsId = stillingsId)
        val kandidatlisteLukketMelding = kandidatlisteLukket(
            aktørIderFikkIkkeJobben = listOf(forespørselTilKandidatSomIkkeFikkJobben.aktørId),
            aktørIderFikkJobben = listOf(forespørselTilKandidatSomFikkJobben.aktørId),
            stillingsId = stillingsId,
            navIdent = "enNavIdent"
        )

        testRapid.sendTestMessage(kandidatlisteLukketMelding)

        val inspektør = mockProducer.history()
        assertThat(inspektør.size).isEqualTo(1)
        val meldingTilAktivitetsplanen = inspektør[0]
        assertThat(meldingTilAktivitetsplanen.key()).isEqualTo(forespørselTilKandidatSomIkkeFikkJobben.forespørselId.toString())
        val meldingBody = objectMapper.readTree(meldingTilAktivitetsplanen.value())
        assertThat(meldingBody.size()).isEqualTo(4)
        assertThat(meldingBody["type"].asText()).isEqualTo("IKKE_FATT_JOBBEN")
        assertThat(meldingBody["detaljer"].asText()).isEqualTo("KANDIDATLISTE_LUKKET_NOEN_ANDRE_FIKK_JOBBEN")
        assertThat(meldingBody["utførtAvNavIdent"].asText()).isEqualTo("enNavIdent")
        assertThat(meldingBody["tidspunkt"].asText()).isEqualTo("2023-02-21T08:38:01.053+01:00")
    }

    @Test
    fun `KandidatlisteLukket-melding skal ikke føre til melding til aktivitetsplanen for kandidater som svarte nei til deling av CV`() {
        val stillingsId = UUID.randomUUID()
        val forespørselSvarteNei =
            lagreForespørsel(aktørId = "aktør1", svarFraBruker = false, stillingsId = stillingsId)
        val forespørselSvarteJa = lagreForespørsel(aktørId = "aktør2", svarFraBruker = true, stillingsId = stillingsId)
        val kandidatlisteLukketMelding = kandidatlisteLukket(
            aktørIderFikkIkkeJobben = listOf(
                forespørselSvarteNei.aktørId,
                forespørselSvarteJa.aktørId
            ), stillingsId = stillingsId, navIdent = "enNavIdent"
        )

        testRapid.sendTestMessage(kandidatlisteLukketMelding)

        assertThat(mockProducer.history().size).isEqualTo(1)
        val meldingTilAktivitetsplanen = mockProducer.history()[0]
        assertThat(meldingTilAktivitetsplanen.key() == forespørselSvarteJa.forespørselId.toString())
    }

    @Test
    fun `KandidatlisteLukket-melding skal ikke føre til melding til aktivitetsplanen for kandidater som aldri svarte på deling av CV`() {
        val stillingsId = UUID.randomUUID()
        val forespørselSvarteIkke = lagreUbesvartForespørsel(aktørId = "aktør1", stillingsId = stillingsId)
        val forespørselSvarteJa = lagreForespørsel(aktørId = "aktør2", svarFraBruker = true, stillingsId = stillingsId)
        val kandidatlisteLukketMelding = kandidatlisteLukket(
            aktørIderFikkIkkeJobben = listOf(
                forespørselSvarteIkke.aktørId,
                forespørselSvarteJa.aktørId
            ), stillingsId = stillingsId, navIdent = "enNavIdent"
        )

        testRapid.sendTestMessage(kandidatlisteLukketMelding)

        assertThat(mockProducer.history().size).isEqualTo(1)
        val meldingTilAktivitetsplanen = mockProducer.history()[0]
        assertThat(meldingTilAktivitetsplanen.key() == forespørselSvarteJa.forespørselId.toString())
    }

    @Test
    fun `KandidatlisteLukket-melding skal ikke føre til melding til aktivitetsplanen for kandidater som aldri ble spurt om deling av CV`() {
        val stillingsId = UUID.randomUUID()
        val aktørIdAldriForespurt = "aktør1"
        val forespørselSvarteJa = lagreForespørsel(aktørId = "aktør2", svarFraBruker = true, stillingsId = stillingsId)
        val kandidatlisteLukketMelding = kandidatlisteLukket(
            aktørIderFikkIkkeJobben = listOf(aktørIdAldriForespurt, forespørselSvarteJa.aktørId),
            stillingsId = stillingsId,
            navIdent = "enNavIdent"
        )

        testRapid.sendTestMessage(kandidatlisteLukketMelding)

        assertThat(mockProducer.history().size).isEqualTo(1)
        val meldingTilAktivitetsplanen = mockProducer.history()[0]
        assertThat(meldingTilAktivitetsplanen.key() == forespørselSvarteJa.forespørselId.toString())
    }

    @Test
    fun `Når hendelse med slutt_av_hendelseskjede satt til true skal ikke noe sendes`() {
        val stillingsId = UUID.randomUUID()
        val forespørsel1 = lagreForespørsel(aktørId = "aktør1", svarFraBruker = true, stillingsId = stillingsId)
        val forespørsel2 = lagreForespørsel(aktørId = "aktør2", svarFraBruker = true, stillingsId = stillingsId)
        val kandidatlisteLukketMelding = kandidatlisteLukket(
            aktørIderFikkIkkeJobben = listOf(forespørsel1.aktørId, forespørsel2.aktørId),
            stillingsId = forespørsel1.stillingsId,
            navIdent = "enNavIdent",
            sluttAvHendelseskjede = true
        )

        testRapid.sendTestMessage(kandidatlisteLukketMelding)

        assertThat(mockProducer.history().size).isZero
        assertThat(testRapid.inspektør.size).isZero
        verify(log, never()).error(any())
    }

    @Test
    fun `Når kandidathendelse kommer skal hendelse republiseres med slutt_av_hendelseskjede satt til true`() {
        val stillingsId = UUID.randomUUID()
        val forespørsel1 = lagreForespørsel(aktørId = "aktør1", svarFraBruker = true, stillingsId = stillingsId)
        val forespørsel2 = lagreForespørsel(aktørId = "aktør2", svarFraBruker = true, stillingsId = stillingsId)
        val kandidatlisteLukketMelding = kandidatlisteLukket(
            aktørIderFikkIkkeJobben = listOf(forespørsel1.aktørId, forespørsel2.aktørId),
            stillingsId = forespørsel1.stillingsId,
            navIdent = "enNavIdent"
        )

        testRapid.sendTestMessage(kandidatlisteLukketMelding)

        assertThat(testRapid.inspektør.size).isEqualTo(1)
        assertThat(testRapid.inspektør.message(0)["@slutt_av_hendelseskjede"].asBoolean()).isTrue
        verify(log, never()).error(any())
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

    private fun lagreUbesvartForespørsel(aktørId: String, stillingsId: UUID = UUID.randomUUID()): Forespørsel {
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

    private fun kandidatlisteLukket(
        aktørIderFikkJobben: List<String> = emptyList(),
        aktørIderFikkIkkeJobben: List<String> = emptyList(),
        stillingsId: UUID,
        navIdent: String,
        sluttAvHendelseskjede: Boolean = false
    ) = """
        {
          "aktørIderFikkJobben": ${aktørIderFikkJobben.joinToJsonArray()},
          "aktørIderFikkIkkeJobben": ${aktørIderFikkIkkeJobben.joinToJsonArray()},
          "organisasjonsnummer": "312113341",
          "kandidatlisteId": "f3f4a72b-1388-4a1b-b808-ed6336e2c6a4",
          "tidspunkt": "2023-02-21T08:38:01.053+01:00",
          "stillingsId": "$stillingsId",
          "utførtAvNavIdent": "$navIdent",
          "@event_name": "kandidat_v2.LukketKandidatliste",
          "@id": "7fa7ab9a-d016-4ed2-9f9a-d1a1ad7018f1",
          "@opprettet": "2023-02-21T08:39:01.937854240",
          ${if (!sluttAvHendelseskjede) "" else """, "@slutt_av_hendelseskjede": $sluttAvHendelseskjede"""}
          "system_read_count": 0
        }
    """.trimIndent()

    private fun List<String>.joinToJsonArray() =
        joinToString(separator = ", ", prefix = "[", postfix = "]") { "\"$it\"" }
}