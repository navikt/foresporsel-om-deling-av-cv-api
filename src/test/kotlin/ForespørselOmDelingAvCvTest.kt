import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.objectBody
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.apache.kafka.clients.producer.MockProducer
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
            aktorIder = listOf("234")
        )

        Fuel.post("http://localhost:8333/foresporsler")
            .medVeilederCookie(mockOAuth2Server)
            .objectBody(inboundDto)
            .response()

        val lagredeForespørsler = repository.hentAlleForespørsler()

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
}
