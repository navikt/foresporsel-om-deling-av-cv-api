import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.objectBody
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import setup.TestDatabase
import setup.medVeilederCookie
import setup.mockProducer
import stilling.Arbeidssted
import stilling.Stilling
import utils.foretrukkenCallIdHeaderKey
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForespørselOmDelingAvCvTest {

    private val database = TestDatabase()
    private val repository = Repository(database.dataSource)
    private val mockProducer = mockProducer()
    private val kafkaService = KafkaService(mockProducer, repository) { enStilling() }

    private val lokalApp = startLokalApp(repository, mockProducer)
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

        Fuel.post("http://localhost:8333/foresporsler")
            .medVeilederCookie(mockOAuth2Server)
            .header(foretrukkenCallIdHeaderKey, callId.toString())
            .objectBody(inboundDto)
            .response()

        val lagredeForespørsler = database.hentAlleForespørsler()

        assertThat(lagredeForespørsler.size).isEqualTo(inboundDto.aktorIder.size)

        val nå = LocalDateTime.now()
        lagredeForespørsler.forEachIndexed { index, lagretForespørsel ->
            assertThat(lagretForespørsel.aktørId).isEqualTo(inboundDto.aktorIder[index])
            assertThat(lagretForespørsel.stillingsId.toString()).isEqualTo(inboundDto.stillingsId)
            assertThat(lagretForespørsel.deltAv).isEqualTo("veileder") // TODO
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
        kafkaService.sendUsendteForespørsler()

        val meldingerSendtPåKafka = mockProducer.history()
        assertThat(meldingerSendtPåKafka.size).isEqualTo(2)

        meldingerSendtPåKafka.map { it.value() }.forEachIndexed { index, forespørsel ->
            assertThat(forespørsel.getAktorId()).isEqualTo(forespørsler[index].aktørId)
            assertThat(forespørsel.getStillingsId()).isEqualTo(forespørsler[index].stillingsId.toString())
            assertThat(LocalDateTime.ofInstant(forespørsel.getOpprettet(), ZoneId.of("UTC"))).isEqualToIgnoringNanos(forespørsler[index].deltTidspunkt)
            assertThat(forespørsel.getOpprettetAv()).isEqualTo(forespørsler[index].deltAv)
            assertThat(forespørsel.getCallId()).isEqualTo(forespørsler[index].callId.toString())

            // TODO: Assert på stilling
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
        kafkaService.sendUsendteForespørsler()

        val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.aktørId }

        assertThat(lagredeForespørsler["123"]!!.deltTidspunkt).isEqualToIgnoringSeconds(nå)
        assertThat(lagredeForespørsler["123"]!!.deltStatus).isEqualTo(DeltStatus.SENDT)

        assertThat(lagredeForespørsler["234"]!!.deltTidspunkt).isEqualToIgnoringSeconds(nå)
        assertThat(lagredeForespørsler["234"]!!.deltStatus).isEqualTo(DeltStatus.SENDT)

        assertThat(lagredeForespørsler["345"]!!.deltTidspunkt).isEqualToIgnoringNanos(enHalvtimeSiden)
        assertThat(lagredeForespørsler["345"]!!.deltStatus).isEqualTo(DeltStatus.SENDT)
    }

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

    private fun enStilling() = Stilling("hoffnarr", "2021-04-08T08:12:51.243199","Kongelige hoff", listOf(
        Arbeidssted("Slottsplassen 1","0001", "OSLO", "OSLO", "OSLO","Norge")))
}
