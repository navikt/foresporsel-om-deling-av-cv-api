import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.objectBody
import com.github.kittinunf.fuel.jackson.responseObject
import mottasvar.Svar
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import setup.TestDatabase
import setup.medVeilederCookie
import utils.foretrukkenCallIdHeaderKey
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
    }

    @Test
    fun `kall til get-endpunkt skala hente lagrede forespørsler på stillingsId`() {
        val database = TestDatabase()

        startLokalApp(database).use {
            val navIdent = "X12345"
            val callId = UUID.randomUUID()
            val stillingsId = UUID.randomUUID()
            val forespørsel = enForespørsel(stillingsId)
            val forespørsler = listOf(
                enForespørsel(UUID.randomUUID()),
                forespørsel,
                enForespørsel(UUID.randomUUID()),
                enForespørsel(UUID.randomUUID()),
            )
            database.lagreBatch(forespørsler)

            val actual = Fuel.get("http://localhost:8333/foresporsler/$stillingsId")
                .medVeilederCookie(mockOAuth2Server, navIdent)
                .header(foretrukkenCallIdHeaderKey, callId.toString())
                .responseObject<List<ForespørselOutboundDto>>().third.get()

            val expected = forespørsel.tilOutboundDto()

            assertThat(actual.size).isEqualTo(1)

            assertEquals(expected, actual[0])
        }
    }

    private fun enForespørsel(stillingsId: UUID, deltStatus: DeltStatus = DeltStatus.SENDT) = Forespørsel(
        id = 0,
        aktørId = "aktørId",
        stillingsId = stillingsId,
        deltStatus = deltStatus,
        deltTidspunkt = LocalDateTime.now(),
        deltAv = "deltAv",
        svar = Svar.IKKE_SVART,
        svarTidspunkt = null,
        sendtTilKafkaTidspunkt = null,
        callId = UUID.randomUUID()
    )
}