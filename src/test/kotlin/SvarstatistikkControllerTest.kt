import auth.TokenHandler.Rolle.ARBEIDSGIVERRETTET
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import setup.TestDatabase
import setup.medToken
import setup.mockProducerAvro
import utils.foretrukkenCallIdHeaderKey
import utils.objectMapper
import java.time.LocalDateTime
import java.time.Month
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SvarstatistikkControllerTest {
    private val mockOAuth2Server = MockOAuth2Server()
    private val wireMock = WireMockServer(WireMockConfiguration.options().port(9089))

    @BeforeAll
    fun init() {
        mockOAuth2Server.start(port = 18300)
        wireMock.start()
    }

    @AfterAll
    fun teardown() {
        mockOAuth2Server.shutdown()
        wireMock.stop()
    }

    @BeforeEach
    fun before() {
        mockProducer.clear()
    }

    private val mockProducer = mockProducerAvro

    @Test
    fun `GET mot statistikk uten rolle skal ha svar ja om det er svar ja i basen innenfor tidsperiode`() {
        val database = TestDatabase()

        startLokalApp(database).use {
            val navIdent = "X12345"
            val callId = UUID.randomUUID()
            val aktørId = "123"
            val navKontor = "0314";

            val stillingUuid = UUID.randomUUID()
            val sendtdato = LocalDateTime.of(2020, Month.APRIL, 3, 0, 0)
            val svartDato = LocalDateTime.of(2021, Month.APRIL, 3, 0, 0)
            val svar = Svar(true, svartDato, Ident(navIdent, IdentType.NAV_IDENT))
            val forespørsel = enForespørsel(
                aktørId = aktørId,
                stillingsId = stillingUuid,
                tilstand = Tilstand.HAR_SVART,
                navKontor = navKontor,
                svar = svar,
                deltTidspunkt = sendtdato
            )

            database.lagreBatch(listOf(forespørsel))


            val fraOgMed = "2020-04-03";
            val tilOgMed = "2020-04-03"

            val lagredeForespørslerForKandidat =
                Fuel.get("http://localhost:8333/statistikk?fraOgMed=${fraOgMed}&tilOgMed=${tilOgMed}&navKontor=${navKontor}")
                    .medToken(mockOAuth2Server, navIdent, emptyList())
                    .header(foretrukkenCallIdHeaderKey, callId.toString())
                    .responseObject<Svarstatistikk>(mapper = objectMapper).third.get()

            assertThat(lagredeForespørslerForKandidat.antallSvartJa).isEqualTo(1)
            assertThat(lagredeForespørslerForKandidat.antallSvartNei).isEqualTo(0)
            assertThat(lagredeForespørslerForKandidat.antallUtløpteSvar).isEqualTo(0)
            assertThat(lagredeForespørslerForKandidat.antallVenterPåSvar).isEqualTo(0)
        }
    }

    @Test
    fun `GET mot statistikk skal ikke ha svar om sendt-dato er etter tidsperiode sendt inn`() {
        val database = TestDatabase()

        startLokalApp(database).use {
            val navIdent = "X12345"
            val callId = UUID.randomUUID()
            val aktørId = "123"
            val navKontor = "0314";

            val stillingUuid = UUID.randomUUID()
            val sendtdato = LocalDateTime.of(2020, Month.APRIL, 3, 0, 0)
            val svartDato = LocalDateTime.of(2021, Month.APRIL, 3, 0, 0)
            val svar = Svar(true, svartDato, Ident(navIdent, IdentType.NAV_IDENT))
            val forespørsel = enForespørsel(
                aktørId = aktørId,
                stillingsId = stillingUuid,
                tilstand = Tilstand.HAR_SVART,
                navKontor = navKontor,
                svar = svar,
                deltTidspunkt = sendtdato
            )

            database.lagreBatch(listOf(forespørsel))


            val fraOgMed = "2020-04-04";
            val tilOgMed = "2020-04-04"

            val lagredeForespørslerForKandidat =
                Fuel.get("http://localhost:8333/statistikk?fraOgMed=${fraOgMed}&tilOgMed=${tilOgMed}&navKontor=${navKontor}")
                    .medToken(mockOAuth2Server, navIdent, listOf(ARBEIDSGIVERRETTET))
                    .header(foretrukkenCallIdHeaderKey, callId.toString())
                    .responseObject<Svarstatistikk>(mapper = objectMapper).third.get()

            assertThat(lagredeForespørslerForKandidat.antallSvartJa).isEqualTo(0)
            assertThat(lagredeForespørslerForKandidat.antallSvartNei).isEqualTo(0)
            assertThat(lagredeForespørslerForKandidat.antallUtløpteSvar).isEqualTo(0)
            assertThat(lagredeForespørslerForKandidat.antallVenterPåSvar).isEqualTo(0)
        }
    }

    @Test
    fun `GET mot statistikk skal ikke ha svar om sendt-dato er før tidsperiode sendt inn`() {
        val database = TestDatabase()

        startLokalApp(database).use {
            val navIdent = "X12345"
            val callId = UUID.randomUUID()
            val aktørId = "123"
            val navKontor = "0314";

            val stillingUuid = UUID.randomUUID()
            val sendtdato = LocalDateTime.of(2020, Month.APRIL, 3, 0, 0)
            val svartDato = LocalDateTime.of(2021, Month.APRIL, 3, 0, 0)
            val svar = Svar(true, svartDato, Ident(navIdent, IdentType.NAV_IDENT))
            val forespørsel = enForespørsel(
                aktørId = aktørId,
                stillingsId = stillingUuid,
                tilstand = Tilstand.HAR_SVART,
                navKontor = navKontor,
                svar = svar,
                deltTidspunkt = sendtdato
            )

            database.lagreBatch(listOf(forespørsel))


            val fraOgMed = "2020-04-02";
            val tilOgMed = "2020-04-02"

            val lagredeForespørslerForKandidat =
                Fuel.get("http://localhost:8333/statistikk?fraOgMed=${fraOgMed}&tilOgMed=${tilOgMed}&navKontor=${navKontor}")
                    .medToken(mockOAuth2Server, navIdent, listOf(ARBEIDSGIVERRETTET))
                    .header(foretrukkenCallIdHeaderKey, callId.toString())
                    .responseObject<Svarstatistikk>(mapper = objectMapper).third.get()

            assertThat(lagredeForespørslerForKandidat.antallSvartJa).isEqualTo(0)
            assertThat(lagredeForespørslerForKandidat.antallSvartNei).isEqualTo(0)
            assertThat(lagredeForespørslerForKandidat.antallUtløpteSvar).isEqualTo(0)
            assertThat(lagredeForespørslerForKandidat.antallVenterPåSvar).isEqualTo(0)
        }
    }

    @Test
    fun `GET mot statistikk skal kunne hente en av hver svartype`() {
        val database = TestDatabase()

        startLokalApp(database).use {
            val navIdent = "X12345"
            val aktørId = "123"
            val navKontor = "0314";

            val stillingUuid = UUID.randomUUID()
            val sendtdato = LocalDateTime.of(2020, Month.APRIL, 3, 0, 0)
            val svartDato = LocalDateTime.of(2021, Month.APRIL, 3, 0, 0)
            val forespørselJa = enForespørsel(
                aktørId = aktørId,
                stillingsId = stillingUuid,
                tilstand = Tilstand.HAR_SVART,
                navKontor = navKontor,
                svar = Svar(true, svartDato, Ident(navIdent, IdentType.NAV_IDENT)),
                deltTidspunkt = sendtdato
            )
            val forespørselNei = enForespørsel(
                aktørId = aktørId,
                stillingsId = stillingUuid,
                tilstand = Tilstand.HAR_SVART,
                navKontor = navKontor,
                svar = Svar(false, svartDato, Ident(navIdent, IdentType.NAV_IDENT)),
                deltTidspunkt = sendtdato
            )
            val forespørselIkkeSvar = enForespørsel(
                aktørId = aktørId,
                stillingsId = stillingUuid,
                tilstand = Tilstand.SVARFRIST_UTLOPT,
                navKontor = navKontor,
                svar = null,
                deltTidspunkt = sendtdato
            )
            val forespørselIkkeSvar2 = enForespørsel(
                aktørId = aktørId,
                stillingsId = stillingUuid,
                tilstand = Tilstand.AVBRUTT,
                navKontor = navKontor,
                svar = null,
                deltTidspunkt = sendtdato
            )
            val forespørselVenter = enForespørsel(
                aktørId = aktørId,
                stillingsId = stillingUuid,
                tilstand = Tilstand.HAR_VARSLET,
                navKontor = navKontor,
                svar = null,
                deltTidspunkt = sendtdato
            )
            val forespørselVenter2 = enForespørsel(
                aktørId = aktørId,
                stillingsId = stillingUuid,
                tilstand = Tilstand.PROVER_VARSLING,
                navKontor = navKontor,
                svar = null,
                deltTidspunkt = sendtdato
            )

            database.lagreBatch(
                listOf(
                    forespørselJa,
                    forespørselNei,
                    forespørselIkkeSvar,
                    forespørselIkkeSvar2,
                    forespørselVenter,
                    forespørselVenter2
                )
            )


            val fraOgMed = "2020-04-02";
            val tilOgMed = "2020-04-04"

            val lagredeForespørslerForKandidat =
                Fuel.get("http://localhost:8333/statistikk?fraOgMed=${fraOgMed}&tilOgMed=${tilOgMed}&navKontor=${navKontor}")
                    .medToken(mockOAuth2Server, navIdent, listOf(ARBEIDSGIVERRETTET))
                    .responseObject<Svarstatistikk>(mapper = objectMapper).third.get()

            assertThat(lagredeForespørslerForKandidat.antallSvartJa).isEqualTo(1)
            assertThat(lagredeForespørslerForKandidat.antallSvartNei).isEqualTo(1)
            assertThat(lagredeForespørslerForKandidat.antallUtløpteSvar).isEqualTo(2)
            assertThat(lagredeForespørslerForKandidat.antallVenterPåSvar).isEqualTo(2)
        }
    }

    @Test
    fun `GET mot statistikk skal returnere 403 Bad Request hvis requesten mangler en query-parameter`() {
        val fraOgMed = "2020-04-02";
        val tilOgMed = "2020-04-04"

        startLokalApp(database = TestDatabase()).use {
            val (_, response) = Fuel.get("http://localhost:8333/statistikk?fraOgMed=${fraOgMed}&tilOgMed=${tilOgMed}")
                .medToken(mockOAuth2Server, "X12345", listOf(ARBEIDSGIVERRETTET)).response()

            assertThat(response.statusCode).isEqualTo(400)
        }
    }
}
