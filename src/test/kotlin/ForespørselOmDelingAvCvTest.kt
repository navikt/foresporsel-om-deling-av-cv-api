import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.objectBody
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import setup.TestDatabase
import setup.medVeilederCookie
import setup.mockProducer
import java.time.LocalDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForespørselOmDelingAvCvTest {

    private val database = TestDatabase()
    private val repository = Repository(database.dataSource)
    private val mockProducer = mockProducer()
    private val kafkaService = KafkaService(repository)
    private val lokalApp = startLokalApp(repository, mockProducer)
    private val mockOAuth2Server = MockOAuth2Server()

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18300)
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

        Fuel.post("http://localhost:8333/foresporsler")
            .medVeilederCookie(mockOAuth2Server)
            .objectBody(inboundDto)
            .response()

        val lagredeForespørsler = database.hentAlleForespørsler()

        assertThat(lagredeForespørsler.size).isEqualTo(inboundDto.aktorIder.size)

        val nå = LocalDateTime.now()
        lagredeForespørsler.forEachIndexed { index, lagretForespørsel ->
            assertThat(lagretForespørsel.aktørId).isEqualTo(inboundDto.aktorIder[index])
            assertThat(lagretForespørsel.stillingsId).isEqualTo(inboundDto.stillingsId)
            assertThat(lagretForespørsel.deltAv).isEqualTo("veileder") // TODO
            assertThat(lagretForespørsel.deltTidspunkt).isBetween(nå.minusMinutes(1), nå)
            assertThat(lagretForespørsel.deltStatus).isEqualTo(DeltStatus.IKKE_SENDT)
            assertThat(lagretForespørsel.svar).isEqualTo(Svar.IKKE_SVART)
            assertThat(lagretForespørsel.svarTidspunkt).isNull()
        }
    }

    @Test
    fun `Usendte forespørsler skal sendes på Kafka og oppdateres med rett status i databasen`() {
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

        assertThat(lagredeForespørsler["123"]!!.deltTidspunkt).isBetween(nå.minusMinutes(1), nå)
        assertThat(lagredeForespørsler["123"]!!.deltStatus).isEqualTo(DeltStatus.SENDT)

        assertThat(lagredeForespørsler["234"]!!.deltTidspunkt).isBetween(nå.minusMinutes(1), nå)
        assertThat(lagredeForespørsler["234"]!!.deltStatus).isEqualTo(DeltStatus.SENDT)

        assertThat(lagredeForespørsler["345"]!!.deltTidspunkt).isEqualToIgnoringNanos(enHalvtimeSiden)
        assertThat(lagredeForespørsler["345"]!!.deltStatus).isEqualTo(DeltStatus.SENDT)
    }

    private fun enForespørsel(aktørId: String, deltStatus: DeltStatus, deltTidspunkt: LocalDateTime = LocalDateTime.now()) = ForespørselOmDelingAvCv(
        id = 0,
        aktørId = aktørId,
        stillingsId = UUID.randomUUID().toString(),
        deltStatus = deltStatus,
        deltTidspunkt = deltTidspunkt,
        deltAv = "veileder",
        svar = Svar.IKKE_SVART,
        svarTidspunkt = null,
        sendtTilKafkaTidspunkt = null
    )
}
