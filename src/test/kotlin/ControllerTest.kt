import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.objectBody
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import setup.TestDatabase
import setup.medVeilederCookie
import stilling.StillingKlient
import utils.foretrukkenCallIdHeaderKey
import utils.objectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ControllerTest {
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

    private val stillingKlient = StillingKlient { mockOAuth2Server.issueToken().serialize() }

    private fun startWiremockApp(
        database: TestDatabase = TestDatabase()
    ) = startLokalApp(database = database, hentStilling = stillingKlient::hentStilling)

    @Test
    fun `Kall til POST-endepunkt skal lagre informasjon om forespørselen i database`() {
        val database = TestDatabase()
        val stillingsReferanse = UUID.randomUUID()
        stubHentStilling(stillingsReferanse)

        startWiremockApp(database).use {
            val inboundDto = ForespørselInboundDto(
                stillingsId = stillingsReferanse.toString(),
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
                assertThat(lagretForespørsel.svarfrist).isEqualTo(inboundDto.svarfrist)
                assertThat(lagretForespørsel.tilstand).isNull()
                assertThat(lagretForespørsel.svar).isNull()
                assertThat(lagretForespørsel.callId).isEqualTo(callId)
                assertThat(lagretForespørsel.forespørselId).isInstanceOf(UUID::class.java)
            }
        }
    }

    @Test
    fun `Kall til POST-endepunkt skal returnere conflict hvis én av kandidatene har mottatt forespørsel på samme stilling fra før`() {
        val database = TestDatabase()
        val stillingsReferanse = UUID.randomUUID()
        stubHentStilling(stillingsReferanse)

        startWiremockApp(database).use {
            val navIdent = "X12345"
            val forespørsel = enForespørsel(stillingsId = stillingsReferanse)

            database.lagreBatch(listOf(forespørsel))

            val inboundDto = ForespørselInboundDto(
                stillingsId = stillingsReferanse.toString(),
                svarfrist = LocalDate.now().plusDays(3).atStartOfDay(),
                aktorIder = listOf(forespørsel.aktørId),
            )

            val (_, response) = Fuel.post("http://localhost:8333/foresporsler")
                .medVeilederCookie(mockOAuth2Server, navIdent)
                .objectBody(inboundDto, mapper = objectMapper)
                .response()

            assertThat(response.statusCode).isEqualTo(409)
        }
    }

    @Test
    fun `Kall til POST-endepunkt skal returnere bad request hvis stillingen ikke er en ekte stilling`() {
        val database = TestDatabase()
        val stillingsReferanse = UUID.randomUUID()

        stubHentStilling(stillingsReferanse, kategori = "FORMIDLING")

        startWiremockApp(database).use {
            val navIdent = "X12345"
            val forespørsel = enForespørsel(stillingsId = stillingsReferanse)

            database.lagreBatch(listOf(forespørsel))

            val inboundDto = ForespørselInboundDto(
                stillingsId = stillingsReferanse.toString(),
                svarfrist = LocalDate.now().plusDays(3).atStartOfDay(),
                aktorIder = listOf(forespørsel.aktørId),
            )

            val (_, response) = Fuel.post("http://localhost:8333/foresporsler")
                .medVeilederCookie(mockOAuth2Server, navIdent)
                .objectBody(inboundDto, mapper = objectMapper)
                .response()

            assertThat(response.statusCode).isEqualTo(400)
        }
    }

    @Test
    fun `Kall til POST-endepunkt skal fungere hvis stillingskategorien er en ekte stilling`() {
        val database = TestDatabase()
        val stillingsReferanse = UUID.randomUUID()

        stubHentStilling(stillingsReferanse, kategori = "STILLING")

        startWiremockApp(database).use {
            val navIdent = "X12345"

            val inboundDto = ForespørselInboundDto(
                stillingsId = stillingsReferanse.toString(),
                svarfrist = LocalDate.now().plusDays(3).atStartOfDay(),
                aktorIder = listOf("123", "345"),
            )

            val (_, response) = Fuel.post("http://localhost:8333/foresporsler")
                .medVeilederCookie(mockOAuth2Server, navIdent)
                .objectBody(inboundDto, mapper = objectMapper)
                .response()

            assertThat(response.statusCode).isEqualTo(201)
        }
    }

    @Test
    fun `Kall til POST-endepunkt skal fungere hvis stillingskategorien er null og derfor skal tolkes som en ekte stilling`() {
        val database = TestDatabase()
        val stillingsReferanse = UUID.randomUUID()

        stubHentStilling(stillingsReferanse, kategori = null)

        startWiremockApp(database).use {
            val navIdent = "X12345"

            val inboundDto = ForespørselInboundDto(
                stillingsId = stillingsReferanse.toString(),
                svarfrist = LocalDate.now().plusDays(3).atStartOfDay(),
                aktorIder = listOf("123", "345"),
            )

            val (_, response) = Fuel.post("http://localhost:8333/foresporsler")
                .medVeilederCookie(mockOAuth2Server, navIdent)
                .objectBody(inboundDto, mapper = objectMapper)
                .response()

            assertThat(response.statusCode).isEqualTo(201)
        }
    }

    @Test
    fun `Kall til POST-endepunkt skal fungere hvis stillingsinfo er null og derfor skal tolkes som en ekte stilling`() {
        val database = TestDatabase()
        val stillingsReferanse = UUID.randomUUID()

        stubHentStilling(stillingsReferanse, stillingsinfo = null)

        startWiremockApp(database).use {
            val navIdent = "X12345"

            val inboundDto = ForespørselInboundDto(
                stillingsId = stillingsReferanse.toString(),
                svarfrist = LocalDate.now().plusDays(3).atStartOfDay(),
                aktorIder = listOf("123", "345"),
            )

            val (_, response) = Fuel.post("http://localhost:8333/foresporsler")
                .medVeilederCookie(mockOAuth2Server, navIdent)
                .objectBody(inboundDto, mapper = objectMapper)
                .response()

            assertThat(response.statusCode).isEqualTo(201)
        }
    }

    @Test
    fun `Kall til GET-endpunkt skal hente lagrede forespørsler på stillingsId`() {
        val database = TestDatabase()
        val stillingsReferanse = UUID.randomUUID()
        stubHentStilling(stillingsReferanse)

        startWiremockApp(database).use {
            val navIdent = "X12345"
            val callId = UUID.randomUUID()
            val forespørsel = enForespørsel(stillingsId = stillingsReferanse)
            val forespørsler = listOf(
                enForespørsel(),
                forespørsel,
                enForespørsel(),
                enForespørsel(),
                enForespørsel(begrunnelseForAtAktivitetIkkeBleOpprettet = BegrunnelseForAtAktivitetIkkeBleOpprettet.UGYLDIG_OPPFOLGINGSSTATUS)
            )

            database.lagreBatch(forespørsler)

            val lagretForespørsel = Fuel.get("http://localhost:8333/foresporsler/$stillingsReferanse")
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
        val stillingsReferanse = UUID.randomUUID()
        stubHentStilling(stillingsReferanse)

        startWiremockApp(database).use {
            val navIdent = "X12345"

            val inboundDto = ForespørselInboundDto(
                stillingsId = stillingsReferanse.toString(),
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
                assertThat(forespørsel.tilstand).isNull()
                assertThat(forespørsel.svar).isNull()
                assertThat(forespørsel.svarfrist).isEqualTo(inboundDto.svarfrist)
            }
        }
    }

    private fun stubHentStilling(stillingsReferanse: UUID?, kategori: String? = null, stillingsinfo: String? = stillingsinfo(kategori)) {
        wireMock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/stilling/_doc/${stillingsReferanse}"))
                .willReturn(
                    WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                                {
                                  "_index": "stilling_15",
                                  "_type": "_doc",
                                  "_id": "3f294b41-9fd6-4a83-b838-5036da69d83c",
                                  "_version": 2,
                                  "_seq_no": 134026,
                                  "_primary_term": 1,
                                  "found": true,
                                  "_source": {
                                    "stilling": {
                                      "title": "En formidling",
                                      "uuid": "3f294b41-9fd6-4a83-b838-5036da69d83c",
                                      "annonsenr": "610647",
                                      "status": "ACTIVE",
                                      "privacy": "INTERNAL_NOT_SHOWN",
                                      "published": "2021-09-24T13:40:11.94403613",
                                      "publishedByAdmin": "2021-09-24T13:40:11.94403613",
                                      "expires": "2021-12-16T02:00:00",
                                      "created": "2021-09-24T13:37:01.9965",
                                      "updated": "2021-09-24T13:37:01.9965",
                                      "employer": {
                                        "name": "FIRST HOUSE AS",
                                        "publicName": "FIRST HOUSE AS",
                                        "orgnr": "994618121",
                                        "parentOrgnr": "994575775",
                                        "orgform": "BEDR"
                                      },
                                      "categories": [
                                        {
                                          "styrkCode": "3433.01",
                                          "name": "Taksidermist"
                                        }
                                      ],
                                      "source": "DIR",
                                      "medium": "DIR",
                                      "businessName": "FIRST HOUSE AS",
                                      "locations": [
                                        {
                                          "address": null,
                                          "postalCode": null,
                                          "city": null,
                                          "county": "VESTFOLD OG TELEMARK",
                                          "countyCode": null,
                                          "municipal": "NOME",
                                          "municipalCode": "3816",
                                          "latitue": null,
                                          "longitude": null,
                                          "country": "NORGE"
                                        }
                                      ],
                                      "reference": "3f294b41-9fd6-4a83-b838-5036da69d83c",
                                      "administration": {
                                        "status": "DONE",
                                        "remarks": [],
                                        "comments": "",
                                        "reportee": "F_Z994003 E_Z994003",
                                        "navIdent": "Z994003"
                                      },
                                      "properties": {
                                        "extent": "Deltid",
                                        "workhours": [
                                          "Dagtid"
                                        ],
                                        "applicationdue": "Snarest",
                                        "workday": [
                                          "Ukedager"
                                        ],
                                        "jobtitle": "Taksidermist",
                                        "positioncount": 1,
                                        "engagementtype": "Prosjekt",
                                        "classification_styrk08_score": 0.5,
                                        "adtext": "<p>tekst</p>",
                                        "classification_styrk08_code": 3212,
                                        "searchtags": [
                                          {
                                            "label": "Bioteknikere (ikke-medisinske laboratorier)",
                                            "score": 0.5
                                          },
                                          {
                                            "label": "Tekniske konservatorer",
                                            "score": 0.5
                                          },
                                          {
                                            "label": "Bioingeniører",
                                            "score": 0.5
                                          }
                                        ],
                                        "classification_esco_code": "http://data.europa.eu/esco/occupation/a78d8e2b-fe31-447f-8694-38ed7198d143",
                                        "classification_input_source": "jobtitle",
                                        "sector": "Privat"
                                      },
                                      "contacts": [
                                        {
                                          "name": "Kulesen",
                                          "role": "",
                                          "title": "Kul",
                                          "email": "",
                                          "phone": "000"
                                        }
                                      ]
                                    },
                                    "stillingsinfo": $stillingsinfo
                                  }
                                }
                            """.trimIndent()
                        )
                )
        )
    }

    private fun stillingsinfo(kategori: String?) =
        """
            {
                "eierNavident": null,
                "eierNavn": null,
                "notat": null,
                "stillingsid": "3f294b41-9fd6-4a83-b838-5036da69d83c",
                "stillingsinfoid": "c4accfe3-55b8-4667-9782-2967e86a1b3e",
                "stillingskategori":   ${kategori?.let { """"$it"""" }}
            }
        """.trimIndent()
}
