import DeltStatus.IKKE_SENDT
import DeltStatus.SENDT
import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.Arbeidssted
import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.KontaktInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import sendforespørsel.ForespørselService
import setup.TestDatabase
import setup.mockProducer
import setup.mockProducerUtenAutocomplete
import stilling.Stilling
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SendForespørselTest {

    @Test
    fun `Usendte forespørsler skal sendes på Kafka`() {
        val database = TestDatabase()
        val mockProducer = mockProducer()
        val stillingFraElasticsearch = enStilling()
        val hentStillingMock: (UUID) -> Stilling? = { stillingFraElasticsearch }

        val forespørselService =
            ForespørselService(mockProducer, Repository(database.dataSource), hentStillingMock)

        startLokalApp(database, producer = mockProducer, forespørselService = forespørselService).use {
            val enHalvtimeSiden = LocalDateTime.now().minusMinutes(30)
            val stillingsId = UUID.randomUUID()

            val forespørsler = listOf(
                enForespørsel("123", deltStatus = DeltStatus.IKKE_SENDT, stillingsId = stillingsId),
                enForespørsel("234", deltStatus = DeltStatus.IKKE_SENDT, stillingsId = stillingsId),
                enForespørsel("345", deltStatus = DeltStatus.SENDT, enHalvtimeSiden)
            )
            
            database.lagreBatch(forespørsler)
            forespørselService.sendUsendte()

            val meldingerSendtPåKafka = mockProducer.history()
            assertThat(meldingerSendtPåKafka.size).isEqualTo(2)

            meldingerSendtPåKafka.map { it.value() }.forEachIndexed { index, actual ->
                val expected = forespørsler[index]

                assertThat(actual.getAktorId()).isEqualTo(expected.aktørId)
                assertThat(actual.getStillingsId()).isEqualTo(expected.stillingsId.toString())
                assertThat(
                    LocalDateTime.ofInstant(actual.getOpprettet(), ZoneId.of("UTC"))
                ).isEqualToIgnoringNanos(
                    expected.deltTidspunkt
                )
                assertThat(actual.getOpprettetAv()).isEqualTo(expected.deltAv)
                assertThat(actual.getCallId()).isEqualTo(expected.callId)
                assertThat(actual.getStillingstittel()).isEqualTo(stillingFraElasticsearch.stillingtittel)
                assertThat(actual.getSoknadsfrist()).isEqualTo(stillingFraElasticsearch.søknadsfrist)

                assertKontaktinfo(actual.getKontaktInfo(), stillingFraElasticsearch.kontaktinfo?.first())

                actual.getArbeidssteder().forEachIndexed { arbeidsstedIndex, actualArbeidssted ->
                    val expectedArbeidssted = stillingFraElasticsearch.arbeidssteder[arbeidsstedIndex]
                    assertArbeidssteder(actualArbeidssted, expectedArbeidssted)
                }
            }
        }
    }

    private fun assertArbeidssteder(actual: Arbeidssted, expected: stilling.Arbeidssted) {
        assertThat(actual.getAdresse()).isEqualTo(expected.adresse)
        assertThat(actual.getPostkode()).isEqualTo(expected.postkode)
        assertThat(actual.getBy()).isEqualTo(expected.by)
        assertThat(actual.getKommune()).isEqualTo(expected.kommune)
        assertThat(actual.getFylke()).isEqualTo(expected.fylke)
        assertThat(actual.getLand()).isEqualTo(expected.land)
    }

    private fun assertKontaktinfo(actual: KontaktInfo, expected: stilling.Kontakt?) {
        assertNotNull(actual)
        assertNotNull(expected)

        assertThat(actual.getNavn()).isEqualTo(expected.navn)
        assertThat(actual.getTittel()).isEqualTo(expected.tittel)
        assertThat(actual.getMobil()).isEqualTo(expected.mobil)
    }

    @Test
    fun `Usendte forespørsler skal oppdateres med rett status i databasen når de sendes på Kafka`() {
        val database = TestDatabase()
        val mockProducer = mockProducer()
        val forespørselService =
            ForespørselService(mockProducer, Repository(database.dataSource), hentStillingMock)

        startLokalApp(database, producer = mockProducer, forespørselService = forespørselService).use {
            val nå = LocalDateTime.now()
            val enHalvtimeSiden = LocalDateTime.now().minusMinutes(30)

            val forespørsler = listOf(
                enForespørsel("123", IKKE_SENDT),
                enForespørsel("234", IKKE_SENDT),
                enForespørsel("345", SENDT, enHalvtimeSiden)
            )

            database.lagreBatch(forespørsler)
            forespørselService.sendUsendte()

            val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.aktørId }

            assertThat(lagredeForespørsler["123"]!!.deltTidspunkt).isEqualToIgnoringSeconds(nå)
            assertThat(lagredeForespørsler["123"]!!.deltStatus).isEqualTo(SENDT)

            assertThat(lagredeForespørsler["234"]!!.deltTidspunkt).isEqualToIgnoringSeconds(nå)
            assertThat(lagredeForespørsler["234"]!!.deltStatus).isEqualTo(SENDT)

            assertThat(lagredeForespørsler["345"]!!.deltTidspunkt).isEqualToIgnoringNanos(enHalvtimeSiden)
            assertThat(lagredeForespørsler["345"]!!.deltStatus).isEqualTo(SENDT)
        }
    }

    @Test
    fun `Usendte forespørsler skal ikke oppdateres status i databasen når sending på Kafka feiler`() {
        val database = TestDatabase()
        val mockProducer = mockProducerUtenAutocomplete()

        val forespørselService =
            ForespørselService(mockProducer, Repository(database.dataSource), hentStillingMock)

        startLokalApp(database, producer = mockProducer, forespørselService = forespørselService).use {
            val enHalvtimeSiden = LocalDateTime.now().minusMinutes(30)

            val forespørsel = enForespørsel("123", IKKE_SENDT, enHalvtimeSiden)

            database.lagreBatch(listOf(forespørsel))
            forespørselService.sendUsendte()

            mockProducer.errorNext(RuntimeException())

            val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.aktørId }

            assertThat(lagredeForespørsler[forespørsel.aktørId]!!.deltTidspunkt).isEqualToIgnoringSeconds(
                enHalvtimeSiden
            )
            assertThat(lagredeForespørsler[forespørsel.aktørId]!!.deltStatus).isEqualTo(IKKE_SENDT)
        }
    }
}
