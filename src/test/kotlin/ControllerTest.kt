import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.objectBody
import com.github.kittinunf.fuel.jackson.responseObject
import mottasvar.Svar
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import setup.TestDatabase
import setup.medVeilederCookie
import utils.foretrukkenCallIdHeaderKey
import utils.objectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ControllerTest {
    private val mockOAuth2Server = MockOAuth2Server()

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18300)
    }

    @AfterAll
    fun teardown() {
        mockOAuth2Server.shutdown()
    }

    @Test
    fun `Kall til endepunkt skal lagre informasjon om forespørselen i database`() {
        val database = TestDatabase()

        startLokalApp(database).use {
            val inboundDto = ForespørselInboundDto(
                stillingsId = UUID.randomUUID().toString(),
                svarfrist = LocalDate.now().plusDays(3).atStartOfDay(),
                aktorIder = listOf("234", "345", "456"),
            )

            val callId = UUID.randomUUID().toString()

            val navIdent = "X12345"

            Fuel.post("http://localhost:8333/foresporsler")
                .medVeilederCookie(mockOAuth2Server, navIdent)
                .header(foretrukkenCallIdHeaderKey, callId)
                .objectBody(inboundDto, mapper = objectMapper)
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
                assertThat(lagretForespørsel.svarfrist).isEqualTo(inboundDto.svarfrist)
                assertThat(lagretForespørsel.svar).isEqualTo(Svar.IKKE_SVART)
                assertThat(lagretForespørsel.svarTidspunkt).isNull()
                assertThat(lagretForespørsel.callId).isEqualTo(callId)
                assertThat(lagretForespørsel.forespørselId).isInstanceOf(UUID::class.java)
            }
        }
    }

    @Test
    fun `Kall til GET-endpunkt skal hente lagrede forespørsler på stillingsId`() {
        val database = TestDatabase()

        startLokalApp(database).use {
            val navIdent = "X12345"
            val callId = UUID.randomUUID()
            val stillingsId = UUID.randomUUID()
            val forespørsel = enForespørsel(stillingsId = stillingsId)
            val forespørsler = listOf(
                enForespørsel(),
                forespørsel,
                enForespørsel(),
                enForespørsel(),
            )

            database.lagreBatch(forespørsler)

            val lagretForespørsel = Fuel.get("http://localhost:8333/foresporsler/$stillingsId")
                .medVeilederCookie(mockOAuth2Server, navIdent)
                .header(foretrukkenCallIdHeaderKey, callId.toString())
                .responseObject<List<ForespørselOutboundDto>>(mapper = objectMapper).third.get()

            val forespørselOutboundDto = forespørsel.tilOutboundDto()

            assertThat(lagretForespørsel.size).isEqualTo(1)
            assertEquals(forespørselOutboundDto, lagretForespørsel[0])
        }
    }

    @Test
    fun `Kall til POST-endepunkt skal returnere lagrede forespørsler på stillingsId`() {
        val database = TestDatabase()

        startLokalApp(database).use {
            val navIdent = "X12345"

            val inboundDto = ForespørselInboundDto(
                stillingsId = UUID.randomUUID().toString(),
                svarfrist = LocalDate.now().plusDays(3).atStartOfDay(),
                aktorIder = listOf("234", "345"),
            )

            val returverdi = Fuel.post("http://localhost:8333/foresporsler/")
                .medVeilederCookie(mockOAuth2Server, navIdent)
                .objectBody(inboundDto, mapper = objectMapper)
                .responseObject<List<ForespørselOutboundDto>>(mapper = objectMapper).third.get()

            assertThat(returverdi.size).isEqualTo(2)

            val nå = LocalDateTime.now()
            returverdi.forEachIndexed { index, forespørsel ->
                assertThat(forespørsel.aktørId).isEqualTo(inboundDto.aktorIder[index])
                assertThat(forespørsel.deltAv).isEqualTo(navIdent)
                assertThat(forespørsel.deltStatus).isEqualTo(DeltStatus.IKKE_SENDT)
                assertThat(forespørsel.deltTidspunkt).isBetween(nå.minusMinutes(1), nå)
                assertThat(forespørsel.svar).isEqualTo(Svar.IKKE_SVART)
                assertThat(forespørsel.svarTidspunkt).isNull()
                assertThat(forespørsel.svarfrist).isEqualTo(inboundDto.svarfrist)
            }
        }
    }
}
