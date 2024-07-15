import auth.TokenHandler
import auth.obo.SimpleTokenValidationContextHolder
import com.github.kittinunf.fuel.Fuel
import com.nimbusds.jwt.SignedJWT
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import setup.medVeilederToken

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TokenTest {

    private val lokalApp = startLokalApp()
    private val mockOAuth2Server = MockOAuth2Server()
    private val tokenHandler = TokenHandler(SimpleTokenValidationContextHolder(), emptyList())

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18300)
    }

    @BeforeEach
    fun setup() {
        tokenHandler.clearCache()
    }

    @AfterAll
    fun teardown() {
        lokalApp.close()
        mockOAuth2Server.shutdown()
    }

    @Test
    fun `Sikrede endepunkter skal returnere 401 hvis requesten ikke inneholder token`() {
        val (_, response, _) = Fuel.post("http://localhost:8333/foresporsler").response()
        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `Sikrede endepunkter skal returnere 401 hvis requesten inneholder et ugyldig token`() {
        val (_, response, _) = Fuel.post("http://localhost:8333/foresporsler")
            .header("Authorization", "Bearer ${hentUgyldigToken().serialize()}")
            .response()

        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `Sikrede endepunkter skal returnere noe annet enn 401 hvis requesten inneholder et gyldig token`() {
        val (_, response, _) = Fuel.post("http://localhost:8333/foresporsler")
            .medVeilederToken(mockOAuth2Server)
            .response()

        assertThat(response.statusCode).isNotEqualTo(401)
    }

    private fun hentUgyldigToken(): SignedJWT {
        return mockOAuth2Server.issueToken(issuerId = "feilissuer")
    }
}
