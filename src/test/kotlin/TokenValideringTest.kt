import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TokenValideringTest {

    private val lokalApp = startLokalApp()
    private val mockOAuth2Server = MockOAuth2Server()

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18300)
    }

    @AfterAll
    fun teardown() {
        lokalApp.close()
    }

    @Test
    fun `Sikrede endepunkter skal returnere 401 hvis requesten ikke inneholder token`() {
        val (_, response, _) = Fuel.post("http://localhost:8333/foresporsler").response()
        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `Sikrede endepunkter skal returnere 401 hvis requesten inneholder et ugyldig token`() {
        val token = hentUgyldigToken(mockOAuth2Server)

        val (_, response, _) = Fuel.post("http://localhost:8333/foresporsler")
            .authentication()
            .bearer(token.serialize())
            .response()

        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `Sikrede endepunkter skal returnere noe annet enn 401 hvis requesten inneholder et gyldig token`() {
        val token = hentToken(mockOAuth2Server)

        val (_, response, _) = Fuel.post("http://localhost:8333/foresporsler")
            .authentication()
            .bearer(token.serialize())
            .response()

        assertThat(response.statusCode).isNotEqualTo(401)
    }

    private fun hentToken(mockOAuth2Server: MockOAuth2Server) = mockOAuth2Server.issueToken("isso-idtoken", "someclientid",
        DefaultOAuth2TokenCallback(
            issuerId = "isso-idtoken",
            claims = mapOf(
                Pair("name", "navn"),
                Pair("NAVident", "NAVident"),
                Pair("unique_name", "unique_name"),
            ),
            audience = listOf("audience")
        )
    )

    private fun hentUgyldigToken(mockOAuth2Server: MockOAuth2Server) = mockOAuth2Server.issueToken("feilissuer", "someclientid",
        DefaultOAuth2TokenCallback(
            issuerId = "feilissuer",
            claims = mapOf(
                Pair("name", "navn"),
                Pair("NAVident", "NAVident"),
                Pair("unique_name", "unique_name"),
            ),
            audience = listOf("audience")
        )
    )
}