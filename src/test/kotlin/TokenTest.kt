import auth.TokenHandler
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import com.nimbusds.jwt.SignedJWT
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import utils.log
import auth.AzureConfig
import auth.TokenClient.TokenResponse
import auth.TokenHandler.Rolle.ARBEIDSGIVERRETTET
import setup.medToken

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TokenTest {

    private val lokalApp = startLokalApp()
    private val mockOAuth2Server = MockOAuth2Server()
    private val tokenHandler = TokenHandler(
        emptyList(),
        Rollekeys("jobbsokerrettetGruppe", "arbeidsgiverrettetGruppe", "utviklerGruppe")
    )

    private val config = AzureConfig(
        azureClientSecret = "clientSecret",
        azureClientId = "clientId",
        tokenEndpoint = "http://localhost:18300/azuread/oauth2/v2.0/token"
    )

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
            .medToken(mockOAuth2Server, "X12345", listOf(ARBEIDSGIVERRETTET))
            .response()

        assertThat(response.statusCode).isNotEqualTo(401)
    }

    @Test
    fun `fetchNewToken skal returnere gyldig token`() {
        val formData = listOf(
            "grant_type" to "client_credentials",
            "client_id" to config.azureClientId,
            "client_secret" to config.azureClientSecret,
            "scope" to "scope"
        )

        val tokenResponse = fetchNewToken(formData)
        assertThat(tokenResponse.access_token).isNotNull()
    }

    private fun fetchNewToken(formData: List<Pair<String, String>>): TokenResponse {
        val (_, response, result) = Fuel.post(config.tokenEndpoint)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(formData.joinToString("&") { "${it.first}=${it.second}" })
            .response()

        return when (result) {
            is Result.Success -> {
                val responseBody = response.body().asString("application/json")
                jacksonObjectMapper().readValue(responseBody, TokenResponse::class.java)
            }
            is Result.Failure -> {
                log.error("Feil ved henting av token: ", result.getException())
                throw RuntimeException("Feil ved henting av token", result.getException())
            }
        }
    }

    private fun hentUgyldigToken(): SignedJWT {
        return mockOAuth2Server.issueToken(issuerId = "feilissuer")
    }
}
