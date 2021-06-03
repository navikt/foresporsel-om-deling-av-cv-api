import mottasvar.Svar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import sendforespørsel.ForespørselService
import setup.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SendForespørselTest {
    @Test
    fun `Usendte forespørsler skal sendes på Kafka`() {
        val database = TestDatabase()
        val mockProducer = mockProducer()
        val forespørselService = ForespørselService(mockProducer,Repository(database.dataSource)) { enStilling() }

        startLokalApp(database, producer = mockProducer, forespørselService = forespørselService).use {
            val enHalvtimeSiden = LocalDateTime.now().minusMinutes(30)

            val forespørsler = listOf(
                enForespørsel("123", DeltStatus.IKKE_SENDT),
                enForespørsel("234", DeltStatus.IKKE_SENDT),
                enForespørsel("345", DeltStatus.SENDT, enHalvtimeSiden)
            )

            database.lagreBatch(forespørsler)
            forespørselService.sendUsendte()

            val meldingerSendtPåKafka = mockProducer.history()
            assertThat(meldingerSendtPåKafka.size).isEqualTo(2)

            val stilling = enStilling()

            meldingerSendtPåKafka.map { it.value() }.forEachIndexed { index, forespørsel ->
                assertThat(forespørsel.getAktorId()).isEqualTo(forespørsler[index].aktørId)
                assertThat(forespørsel.getStillingsId()).isEqualTo(forespørsler[index].stillingsId.toString())
                assertThat(
                    LocalDateTime.ofInstant(
                        forespørsel.getOpprettet(),
                        ZoneId.of("UTC")
                    )
                ).isEqualToIgnoringNanos(forespørsler[index].deltTidspunkt)
                assertThat(forespørsel.getOpprettetAv()).isEqualTo(forespørsler[index].deltAv)
                assertThat(forespørsel.getCallId()).isEqualTo(forespørsler[index].callId.toString())
                assertThat(forespørsel.getStillingstittel()).isEqualTo(stilling.stillingtittel)
                assertThat(forespørsel.getSoknadsfrist()).isEqualTo(stilling.søknadsfrist)

                forespørsel.getArbeidssteder().forEachIndexed { arbeidsstedIndex, arbeidssted ->
                    assertThat(arbeidssted.getAdresse()).isEqualTo(stilling.arbeidssteder[arbeidsstedIndex].adresse)
                    assertThat(arbeidssted.getPostkode()).isEqualTo(stilling.arbeidssteder[arbeidsstedIndex].postkode)
                    assertThat(arbeidssted.getBy()).isEqualTo(stilling.arbeidssteder[arbeidsstedIndex].by)
                    assertThat(arbeidssted.getKommune()).isEqualTo(stilling.arbeidssteder[arbeidsstedIndex].kommune)
                    assertThat(arbeidssted.getFylke()).isEqualTo(stilling.arbeidssteder[arbeidsstedIndex].fylke)
                    assertThat(arbeidssted.getLand()).isEqualTo(stilling.arbeidssteder[arbeidsstedIndex].land)
                }
            }
        }
    }

    @Test
    fun `Usendte forespørsler skal oppdateres med rett status i databasen når de sendes på Kafka`() {
        val database = TestDatabase()
        val mockProducer = mockProducer()
        val forespørselService = ForespørselService(mockProducer,Repository(database.dataSource)) { enStilling() }

        startLokalApp(database, producer = mockProducer, forespørselService = forespørselService).use {
            val nå = LocalDateTime.now()
            val enHalvtimeSiden = LocalDateTime.now().minusMinutes(30)

            val forespørsler = listOf(
                enForespørsel("123", DeltStatus.IKKE_SENDT),
                enForespørsel("234", DeltStatus.IKKE_SENDT),
                enForespørsel("345", DeltStatus.SENDT, enHalvtimeSiden)
            )

            database.lagreBatch(forespørsler)
            forespørselService.sendUsendte()

            val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.aktørId }

            assertThat(lagredeForespørsler["123"]!!.deltTidspunkt).isEqualToIgnoringSeconds(nå)
            assertThat(lagredeForespørsler["123"]!!.deltStatus).isEqualTo(DeltStatus.SENDT)

            assertThat(lagredeForespørsler["234"]!!.deltTidspunkt).isEqualToIgnoringSeconds(nå)
            assertThat(lagredeForespørsler["234"]!!.deltStatus).isEqualTo(DeltStatus.SENDT)

            assertThat(lagredeForespørsler["345"]!!.deltTidspunkt).isEqualToIgnoringNanos(enHalvtimeSiden)
            assertThat(lagredeForespørsler["345"]!!.deltStatus).isEqualTo(DeltStatus.SENDT)
        }
    }

    private fun enForespørsel(aktørId: String, deltStatus: DeltStatus, deltTidspunkt: LocalDateTime = LocalDateTime.now(), stillingsId: UUID = UUID.randomUUID(), deltAv: String = "veileder") = Forespørsel(
        id = 0,
        aktørId = aktørId,
        stillingsId = stillingsId,
        deltStatus = deltStatus,
        deltTidspunkt = deltTidspunkt,
        deltAv = deltAv,
        svar = Svar.IKKE_SVART,
        svarTidspunkt = null,
        sendtTilKafkaTidspunkt = null,
        callId = UUID.randomUUID()
    )
}
