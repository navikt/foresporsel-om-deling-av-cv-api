import stilling.Arbeidssted
import stilling.Kontakt
import stilling.Stilling
import java.time.LocalDate
import java.time.LocalDateTime
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

val hentStillingMock: (UUID) -> Stilling? = { enStilling() }

fun enForespørsel(
    aktørId: String = "aktørId",
    deltStatus: DeltStatus = DeltStatus.SENDT,
    deltTidspunkt: LocalDateTime = LocalDateTime.now().withNano(0),
    stillingsId: UUID = UUID.randomUUID(),
    forespørselId: UUID = UUID.randomUUID(),
    deltAv: String = "veileder",
    begrunnelseForAtAktivitetIkkeBleOpprettet: BegrunnelseForAtAktivitetIkkeBleOpprettet? = null
) = Forespørsel(
    id = 0,
    aktørId = aktørId,
    stillingsId = stillingsId,
    forespørselId = forespørselId,
    deltStatus = deltStatus,
    deltTidspunkt = deltTidspunkt,
    deltAv = deltAv,
    svarfrist = LocalDate.now().plusDays(6).atStartOfDay(),
    tilstand = null,
    svar = null,
    begrunnelseForAtAktivitetIkkeBleOpprettet = begrunnelseForAtAktivitetIkkeBleOpprettet,
    sendtTilKafkaTidspunkt = null,
    callId = UUID.randomUUID().toString(),
)

