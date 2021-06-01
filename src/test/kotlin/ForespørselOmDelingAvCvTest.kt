import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.objectBody
import no.nav.rekrutteringsbistand.avro.SvarPaDelingAvCvKafkamelding
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import sendforespørsel.ForespørselService
import setup.*
import utils.foretrukkenCallIdHeaderKey
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForespørselOmDelingAvCvTest {

    private val database = TestDatabase()
    private val repository = Repository(database.dataSource)
    private val mockProducer = mockProducer()
    private val forespørselService = ForespørselService(mockProducer, repository) { enStilling() }

    private val lokalApp = startLokalApp(database, repository, mockProducer, forespørselService)
    private val mockOAuth2Server = MockOAuth2Server()

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18300)
    }

    @BeforeEach
    fun beforeEach() {
        database.slettAlt()
    }

    @AfterAll
    fun teardown() {
        lokalApp.close()
        mockOAuth2Server.shutdown()
    }

    @Test
    fun `Kall til endepunkt skal lagre informasjon om forespørselen i database`() {
        val inboundDto = ForespørselOmDelingAvCvInboundDto(
            stillingsId = UUID.randomUUID().toString(),
            aktorIder = listOf("234", "345", "456")
        )
        val callId = UUID.randomUUID()

        val navIdent = "X12345"

        Fuel.post("http://localhost:8333/foresporsler")
            .medVeilederCookie(mockOAuth2Server, navIdent)
            .header(foretrukkenCallIdHeaderKey, callId.toString())
            .objectBody(inboundDto)
            .response()

        val lagredeForespørsler = database.hentAlleForespørsler()

        assertThat(lagredeForespørsler.size).isEqualTo(inboundDto.aktorIder.size)

        val nå = LocalDateTime.now()
        lagredeForespørsler.forEachIndexed { index, lagretForespørsel ->
            assertThat(lagretForespørsel.aktørId).isEqualTo(inboundDto.aktorIder[index])
            assertThat(lagretForespørsel.stillingsId.toString()).isEqualTo(inboundDto.stillingsId)
            assertThat(lagretForespørsel.deltAv).isEqualTo(navIdent)
            assertThat(lagretForespørsel.deltTidspunkt).isBetween(nå.minusMinutes(1), nå)
            assertThat(lagretForespørsel.deltStatus).isEqualTo(DeltStatus.IKKE_SENDT)
            assertThat(lagretForespørsel.svar).isEqualTo(Svar.IKKE_SVART)
            assertThat(lagretForespørsel.svarTidspunkt).isNull()
            assertThat(lagretForespørsel.callId).isEqualTo(callId)
        }
    }

    @Test
    fun `Usendte forespørsler skal sendes på Kafka`() {
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
            assertThat(LocalDateTime.ofInstant(forespørsel.getOpprettet(), ZoneId.of("UTC"))).isEqualToIgnoringNanos(forespørsler[index].deltTidspunkt)
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

    @Test
    fun `Usendte forespørsler skal oppdateres med rett status i databasen når de sendes på Kafka`() {
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

    @Test
    fun `Mottatt svar skal oppdatere riktig forespørsel i databasen`() {
        val consumer = mockConsumer()

        val forespørsel = enForespørsel("123", DeltStatus.SENDT)
        database.lagreBatch(listOf(forespørsel))

        val svarKafkamelding = SvarPaDelingAvCvKafkamelding(
            forespørsel.aktørId, forespørsel.stillingsId.toString(), Svar.JA.toString(), null
        )

        mottaSvarKafkamelding(consumer, svarKafkamelding)

        assertMedTimeout(2) {
            val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.aktørId }

            val svarEndretTilJa = lagredeForespørsler["123"]!!.svar == Svar.JA

            if (!svarEndretTilJa) {
                Thread.sleep(100)
            }

            svarEndretTilJa
        }
    }

    private fun assertMedTimeout(timeoutSekunder: Int, conditional: (Int) -> Boolean) =
        assertTrue((0..(timeoutSekunder*10)).any(conditional))

    private fun enForespørsel(aktørId: String, deltStatus: DeltStatus, deltTidspunkt: LocalDateTime = LocalDateTime.now()) = ForespørselOmDelingAvCv(
        id = 0,
        aktørId = aktørId,
        stillingsId = UUID.randomUUID(),
        deltStatus = deltStatus,
        deltTidspunkt = deltTidspunkt,
        deltAv = "veileder",
        svar = Svar.IKKE_SVART,
        svarTidspunkt = null,
        sendtTilKafkaTidspunkt = null,
        callId = UUID.randomUUID()
    )
}
