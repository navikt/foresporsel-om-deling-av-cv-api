import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.objectBody
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.ForesporselOmDelingAvCv
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import setup.TestDatabase
import setup.medVeilederCookie
import setup.mockProducer
import stilling.StillingKlient
import utils.foretrukkenCallIdHeaderKey
import utils.objectMapper
import java.time.*
import java.util.*
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ControllerTest {
    private val mockOAuth2Server = MockOAuth2Server()
    private val wireMock = WireMockServer(WireMockConfiguration.options().port(9089))

    @BeforeAll
    fun init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Oslo"))

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

    private val stillingKlient = StillingKlient { mockOAuth2Server.issueToken().serialize() }
    private val mockProducer = mockProducer()

    private fun startWiremockApp(
        database: TestDatabase = TestDatabase()
    ) = startLokalApp(database = database, hentStilling = stillingKlient::hentStilling, producer = mockProducer)

    @Test
    fun `Kall til POST-endepunkt skal lagre informasjon om forespørselen i database`() {
        val database = TestDatabase()
        val stillingsReferanse = UUID.randomUUID()
        stubHentStilling(stillingsReferanse)
        val aktørid1 = "234"
        val aktørid2 = "345"
        val aktørid3 = "456"

        startWiremockApp(database).use {
            val inboundDto = ForespørselInboundDto(
                stillingsId = stillingsReferanse.toString(),
                svarfrist = omTreDager,
                aktorIder = listOf(aktørid1, aktørid2, aktørid3),
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

            assertThat(mockProducer.history()).hasSize(3)
            val actualMeldinger: Map<String, ForesporselOmDelingAvCv> =
                mockProducer.history().map { it.value() }.associateBy { it.getAktorId() }
            assertThat(actualMeldinger).hasSameSizeAs(mockProducer.history())

            val aktørIdsOnOutboundTopic = mockProducer.history().map { it.value().getAktorId() }
            assertThat(aktørIdsOnOutboundTopic).containsExactlyInAnyOrder(aktørid1, aktørid2, aktørid3)

            val aktør1: ForesporselOmDelingAvCv = actualMeldinger[aktørid1] ?: fail("Vi fant ikke aktørid1")
            assertThat(aktør1.getArbeidsgiver()).isEqualTo("FIRST HOUSE AS")
            assertThat(aktør1.getArbeidssteder()).hasSize(1)
            aktør1.getArbeidssteder().first().apply {
                assertThat(getAdresse()).isNull()
                assertThat(getBy()).isNull()
                assertThat(getFylke()).isEqualTo("VESTFOLD OG TELEMARK")
                assertThat(getLand()).isEqualTo("NORGE")
                assertThat(getKommune()).isEqualTo("NOME")
                assertThat(getPostkode()).isNull()
            }
            lagredeForespørsler.find { it.aktørId == aktørid1 }.let {
                assertThat(aktør1.getBestillingsId()).isEqualTo(it?.forespørselId?.toString())
                assertThat(aktør1.getCallId()).isEqualTo(it?.callId)
                assertThat(it?.deltTidspunkt).isBetween(LocalDateTime.now().minusSeconds(10), LocalDateTime.now())
                assertThat(aktør1.getOpprettetAv()).isEqualTo(it?.deltAv)
            }
            aktør1.getKontaktInfo().apply {
                assertThat(this.getMobil()).isEqualTo("000")
                assertThat(this.getNavn()).isEqualTo("Kulesen")
                assertThat(this.getTittel()).isEqualTo("Kul")
            }

            assertThat(aktør1.getSoknadsfrist()).isEqualTo("Snarest")
            assertThat(aktør1.getStillingstittel()).isEqualTo("En formidling")
            assertThat(aktør1.getStillingsId()).isEqualTo(inboundDto.stillingsId)
            assertThat(aktør1.getSvarfrist()).isEqualTo(inboundDto.svarfrist.toInstant())
        }
    }

    @Test
    fun `Kall til POST-endepunkt, med stilling med søknadsfrist, skal sende den videre på kafka`() {
        val database = TestDatabase()
        val stillingsReferanse = UUID.randomUUID()
        val soknadsFrist = "Snarest"
        stubHentStilling(stillingsReferanse, soknadsFrist = soknadsFrist)

        startWiremockApp(database).use {
            val inboundDto = ForespørselInboundDto(
                stillingsId = stillingsReferanse.toString(),
                svarfrist = omTreDager,
                aktorIder = listOf("234"),
            )

            val callId = UUID.randomUUID().toString()

            val navIdent = "X12345"

            Fuel.post("http://localhost:8333/foresporsler")
                .medVeilederCookie(mockOAuth2Server, navIdent)
                .header(foretrukkenCallIdHeaderKey, callId)
                .objectBody(inboundDto, mapper = objectMapper)
                .response()

            assertEquals(1, mockProducer.history().size)
            assertEquals(soknadsFrist, mockProducer.history()[0].value().getSoknadsfrist())
        }
    }

    @Test
    fun `Kall til POST-endepunkt, med stilling uten søknadsfrist, skal sende uten søknadsfrist på kafka`() {
        val database = TestDatabase()
        val stillingsReferanse = UUID.randomUUID()
        val soknadsFrist = null
        stubHentStilling(stillingsReferanse, soknadsFrist = soknadsFrist)

        startWiremockApp(database).use {
            val inboundDto = ForespørselInboundDto(
                stillingsId = stillingsReferanse.toString(),
                svarfrist = omTreDager,
                aktorIder = listOf("234"),
            )

            val callId = UUID.randomUUID().toString()

            val navIdent = "X12345"

            Fuel.post("http://localhost:8333/foresporsler")
                .medVeilederCookie(mockOAuth2Server, navIdent)
                .header(foretrukkenCallIdHeaderKey, callId)
                .objectBody(inboundDto, mapper = objectMapper)
                .response()

            assertEquals(1, mockProducer.history().size)
            assertEquals(soknadsFrist, mockProducer.history()[0].value().getSoknadsfrist())
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
                svarfrist = omTreDager,
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
                svarfrist = omTreDager,
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
                svarfrist = omTreDager,
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
                svarfrist = omTreDager,
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
                svarfrist = omTreDager,
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
    fun `Kall til GET-endpunkt skal hente lagrede forespørsler på stillingsId gruppert på aktørId`() {
        val database = TestDatabase()
        val stillingsReferanse = UUID.randomUUID()
        stubHentStilling(stillingsReferanse)

        startWiremockApp(database).use {
            val navIdent = "X12345"
            val callId = UUID.randomUUID()
            val aktørId = "aktørId"
            val annenAktørId = "annenAktørId"

            val forespørslerGruppertPåKandidat = hashMapOf(
                aktørId to listOf(
                    enForespørsel(aktørId = aktørId, stillingsId = stillingsReferanse),
                ),
                annenAktørId to listOf(
                    enForespørsel(aktørId = annenAktørId, stillingsId = stillingsReferanse),
                    enForespørsel(aktørId = annenAktørId, stillingsId = stillingsReferanse),
                )
            )
            val alleForespørsler = forespørslerGruppertPåKandidat.flatMap { it.value }
            database.lagreBatch(alleForespørsler)

            val kandidaterMedForespørsler = Fuel.get("http://localhost:8333/foresporsler/$stillingsReferanse")
                .medVeilederCookie(mockOAuth2Server, navIdent)
                .header(foretrukkenCallIdHeaderKey, callId.toString())
                .responseObject<ForespørslerGruppertPåAktørId>(mapper = objectMapper)
                .third
                .get()

            assertThat(kandidaterMedForespørsler.size).isEqualTo(2)
            assertThat(kandidaterMedForespørsler[aktørId]?.size).isEqualTo(forespørslerGruppertPåKandidat[aktørId]?.size)
            assertThat(kandidaterMedForespørsler[annenAktørId]?.size).isEqualTo(forespørslerGruppertPåKandidat[annenAktørId]?.size)
        }
    }

    @Test
    fun `Kall til GET-endpunkt for kandidat skal hente gjeldende forespørsler på aktørId`() {
        val database = TestDatabase()

        startLokalApp(database).use {
            val navIdent = "X12345"
            val callId = UUID.randomUUID()
            val aktørId = "123"

            val stillingUuid = UUID.randomUUID()
            val gammelForespørselForStillingen = enForespørsel(aktørId = aktørId, stillingsId = stillingUuid)
            val gjeldendeForespørselForStillingen = enForespørsel(aktørId = aktørId, stillingsId = stillingUuid)
            val forespørselForEnAnnenStilling = enForespørsel(aktørId = aktørId)

            database.lagreBatch(listOf(gammelForespørselForStillingen))

            database.lagreBatch(
                listOf(
                    gjeldendeForespørselForStillingen,
                    forespørselForEnAnnenStilling
                )
            )

            val lagredeForespørslerForKandidat = Fuel.get("http://localhost:8333/foresporsler/kandidat/$aktørId")
                .medVeilederCookie(mockOAuth2Server, navIdent)
                .header(foretrukkenCallIdHeaderKey, callId.toString())
                .responseObject<List<ForespørselOutboundDto>>(mapper = objectMapper).third.get()

            assertThat(lagredeForespørslerForKandidat.size).isEqualTo(2)
            // assertThat(lagredeForespørslerForKandidat).containsExactlyInAnyOrder(
            //    gjeldendeForespørselForStillingen.tilOutboundDto(),
            //    forespørselForEnAnnenStilling.tilOutboundDto()
            //)
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
                svarfrist = omTreDager,
                aktorIder = listOf("234", "345"),
            )

            val returverdi = Fuel.post("http://localhost:8333/foresporsler/")
                .medVeilederCookie(mockOAuth2Server, navIdent)
                .objectBody(inboundDto, mapper = objectMapper)
                .responseObject<ForespørslerGruppertPåAktørId>(mapper = objectMapper).third.get()

            assertThat(returverdi.size).isEqualTo(2)

            val nå = LocalDateTime.now()
            returverdi.values.flatten().forEach { forespørsel ->
                assertThat(forespørsel.aktørId).isIn(inboundDto.aktorIder)
                assertThat(forespørsel.deltAv).isEqualTo(navIdent)
                assertThat(forespørsel.deltStatus).isEqualTo(DeltStatus.IKKE_SENDT)
                assertThat(forespørsel.deltTidspunkt).isBetween(nå.minusMinutes(1), nå)
                assertThat(forespørsel.tilstand).isNull()
                assertThat(forespørsel.svar).isNull()
                assertThat(forespørsel.svarfrist).isEqualTo(inboundDto.svarfrist)
            }
        }
    }

    @Test
    fun `Kall til POST-endepunkt for resending skal gi 400 hvis kandidaten ikke har fått forespørsel før`() {
        val aktørId = "dummyAktørId"
        val inboundDto = ResendForespørselInboundDto(
            stillingsId = UUID.randomUUID().toString(),
            svarfrist = omTreDager
        )

        startWiremockApp().use {
            val (_, response) = Fuel.post("http://localhost:8333/foresporsler/kandidat/$aktørId")
                .medVeilederCookie(mockOAuth2Server, "A123456")
                .objectBody(inboundDto, mapper = objectMapper)
                .response()

            assertThat(response.statusCode).isEqualTo(400)
        }
    }

    @Test
    fun `Kall til POST-endepunkt for resending skal returnere alle forespørsler på stillingsId`() {
        val aktørId = "dummyAktørId"

        val database = TestDatabase()
        startWiremockApp().use {
            val stillingsId = UUID.randomUUID()

            val forespørselMedUtgåttSvarfrist = enForespørsel(
                aktørId = aktørId,
                stillingsId = stillingsId,
                tilstand = Tilstand.AVBRUTT
            )

            val enAnnenLagretForespørsel = enForespørsel(
                aktørId = "enAnnenAktør",
                stillingsId = stillingsId
            )

            database.lagreBatch(
                listOf(
                    forespørselMedUtgåttSvarfrist,
                    enAnnenLagretForespørsel
                )
            )

            val inboundDto = ResendForespørselInboundDto(
                stillingsId = stillingsId.toString(),
                svarfrist = omTreDager
            )

            val (_, response, result) = Fuel.post("http://localhost:8333/foresporsler/kandidat/$aktørId")
                .medVeilederCookie(mockOAuth2Server, "A123456")
                .objectBody(inboundDto, mapper = objectMapper)
                .responseObject<ForespørslerGruppertPåAktørId>(mapper = objectMapper)

            val outboundDto = result.get()

            assertThat(response.statusCode).isEqualTo(201)
            assertThat(outboundDto.size).isEqualTo(2)
            assertThat(outboundDto[aktørId]?.size).isEqualTo(2)
            assertThat(outboundDto[enAnnenLagretForespørsel.aktørId]?.size).isEqualTo(1)
        }
    }

    @Test
    fun `Kall til POST-endepunkt for resending skal gi 400 hvis siste forespørsel for kandidat ikke er besvart`() {
        val aktørId = "dummyAktørId"
        val stillingsId = UUID.randomUUID()

        stubHentStilling(stillingsId)
        val database = TestDatabase()
        startWiremockApp().use {
            val gammelForespørselMedUtgåttSvarfrist = enForespørsel(
                aktørId = aktørId,
                stillingsId = stillingsId,
                tilstand = Tilstand.AVBRUTT
            )
            val ubesvartForespørsel = enForespørsel(
                aktørId = aktørId,
                stillingsId = stillingsId,
                tilstand = Tilstand.HAR_VARSLET
            )

            database.lagreBatch(
                listOf(
                    gammelForespørselMedUtgåttSvarfrist,
                    ubesvartForespørsel
                )
            )

            val nyForespørsel = ResendForespørselInboundDto(
                stillingsId = stillingsId.toString(),
                svarfrist = omTreDager
            )

            val (_, response) = Fuel.post("http://localhost:8333/foresporsler/kandidat/$aktørId")
                .medVeilederCookie(mockOAuth2Server, "A123456")
                .objectBody(nyForespørsel, mapper = objectMapper)
                .response()

            assertThat(response.statusCode).isEqualTo(400)
        }
    }

    @Test
    fun `Kall til POST-endepunkt for resending skal gi 400 hvis siste forespørsel for kandidat er besvart med ja`() {
        val aktørId = "dummyAktørId"
        val stillingsId = UUID.randomUUID()

        stubHentStilling(stillingsId)
        val database = TestDatabase()
        startWiremockApp().use {

            val godkjentForespørsel = enForespørsel(
                aktørId = aktørId,
                stillingsId = stillingsId,
                tilstand = Tilstand.HAR_SVART,
                svar = Svar(
                    harSvartJa = true,
                    svarTidspunkt = LocalDateTime.now(),
                    svartAv = Ident(aktørId, IdentType.AKTOR_ID)
                )
            )

            database.lagreBatch(
                listOf(
                    godkjentForespørsel
                )
            )

            val nyForespørsel = ResendForespørselInboundDto(
                stillingsId = stillingsId.toString(),
                svarfrist = omTreDager
            )

            val (_, response) = Fuel.post("http://localhost:8333/foresporsler/kandidat/$aktørId")
                .medVeilederCookie(mockOAuth2Server, "A123456")
                .objectBody(nyForespørsel, mapper = objectMapper)
                .response()

            assertThat(response.statusCode).isEqualTo(400)
        }
    }

    private fun stubHentStilling(
        stillingsReferanse: UUID?,
        kategori: String? = null,
        stillingsinfo: String? = stillingsinfo(kategori),
        soknadsFrist: String? = "Snarest"
    ) {
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
                                        "applicationdue": ${soknadsFrist?.let { """"$it"""" }},
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

val omTreDager = ZonedDateTime.of(LocalDate.now().plusDays(3).atStartOfDay(), ZoneId.of("Europe/Oslo"))