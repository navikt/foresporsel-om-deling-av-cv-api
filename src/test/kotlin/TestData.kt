import stilling.Arbeidssted
import stilling.RekbisKontaktinfo
import stilling.RekbisStilling
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
    )
)

val hentStillingMock: (UUID) -> Stilling? = { enStilling() }

val rekbisStilling =
    RekbisStilling("anyId", RekbisKontaktinfo("anyNavn", "anyTittel", "anyTlfnr", "anyEpostadr")) // TODO Are
val hentRekbisStillingMock: (UUID) -> RekbisStilling? = { rekbisStilling }


fun enForespørsel(
    aktørId: String = "aktørId",
    deltStatus: DeltStatus = DeltStatus.SENDT,
    deltTidspunkt: LocalDateTime = LocalDateTime.now().withNano(0),
    stillingsId: UUID = UUID.randomUUID(),
    forespørselId: UUID = UUID.randomUUID(),
    deltAv: String = "veileder"
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
    sendtTilKafkaTidspunkt = null,
    callId = UUID.randomUUID().toString()
)
