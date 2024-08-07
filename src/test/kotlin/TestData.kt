import auth.AzureConfig
import stilling.Arbeidssted
import stilling.Kontakt
import stilling.Stilling
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

fun enStilling() = Stilling(
    "hoffnarr",
    "2021-04-08T08:12:51.243199",
    "Kongelige hoff",
    listOf(
        Arbeidssted("Slottsplassen 1", "0001", "OSLO", "OSLO", "OSLO", "Norge")
    ),
    listOf(
        Kontakt("Ola", "Veileder", "ola@nav.no", "", "")
    ),
    "STILLING"
)

val enNavIdent = "A100001"

val hentStillingMock: (UUID) -> Stilling? = { enStilling() }

val testAzureConfig = AzureConfig(
    azureClientSecret = "test-secret",
    azureClientId = "test-client-id",
    tokenEndpoint = "http://localhost:18300/azuread/oauth2/v2.0/token"
)


fun enForespørsel(
    aktørId: String = "aktørId",
    deltStatus: DeltStatus = DeltStatus.SENDT,
    deltTidspunkt: LocalDateTime = LocalDateTime.now().withNano(0),
    stillingsId: UUID = UUID.randomUUID(),
    forespørselId: UUID = UUID.randomUUID(),
    deltAv: String = "veileder",
    tilstand: Tilstand? = null,
    svar: Svar? = null,
    svarfrist: ZonedDateTime = LocalDate.now().plusDays(6).atStartOfDay().atZone(ZoneId.of("Europe/Oslo")),
    begrunnelseForAtAktivitetIkkeBleOpprettet: BegrunnelseForAtAktivitetIkkeBleOpprettet? = null,
    navKontor: String = "1234"
) = Forespørsel(
    id = 0,
    aktørId = aktørId,
    stillingsId = stillingsId,
    forespørselId = forespørselId,
    deltStatus = deltStatus,
    deltTidspunkt = deltTidspunkt,
    deltAv = deltAv,
    svarfrist = svarfrist,
    tilstand = tilstand,
    svar = svar,
    begrunnelseForAtAktivitetIkkeBleOpprettet = begrunnelseForAtAktivitetIkkeBleOpprettet,
    sendtTilKafkaTidspunkt = null,
    callId = UUID.randomUUID().toString(),
    navKontor = navKontor
)

