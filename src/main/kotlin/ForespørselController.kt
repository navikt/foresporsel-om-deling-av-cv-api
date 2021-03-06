import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.javalin.http.Context
import stilling.Stilling
import utils.hentCallId
import utils.log
import utils.toUUID
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

const val stillingsIdParamName = "stillingsId"
const val aktorIdParamName = "aktørId"

class ForespørselController(
    private val repository: Repository,
    sendUsendteForespørsler: () -> Unit,
    hentStilling: (UUID) -> Stilling?
) {
    val hentForespørsler: (Context) -> Unit = { ctx ->
        try {
            ctx.pathParam(stillingsIdParamName)
        } catch (exception: IllegalArgumentException) {
            ctx.status(400)
            null
        }?.let { stillingsId ->
            ctx.json(hentForespørslerGruppertPåAktørId(stillingsId))
            ctx.status(200)
        }
    }

    val hentForespørslerForKandidat: (Context) -> Unit = { ctx ->
        try {
            ctx.pathParam(aktorIdParamName)
        } catch (exception: IllegalArgumentException) {
            ctx.status(400)
            null
        }?.let { aktørId ->
            val alleForespørslerForKandidat = repository.hentForespørslerForKandidat(aktørId)
            val gjeldendeForespørslerForKandidat = alleForespørslerForKandidat.associateBy { it.stillingsId }.values

            val outboundDto = gjeldendeForespørslerForKandidat.map(Forespørsel::tilOutboundDto)
            ctx.json(outboundDto)
            ctx.status(200)
        }
    }

    val sendForespørselOmDelingAvCv: (Context) -> Unit = { ctx ->
        val forespørselOmDelingAvCvDto = try {
            ctx.bodyAsClass(ForespørselInboundDto::class.java)
        } catch (e: MissingKotlinParameterException) {
            null
        }

        if (forespørselOmDelingAvCvDto == null) {
            ctx.status(400).json("Ugyldig input")
        } else {
            val minstEnKandidatHarFåttForespørselFør: () -> Boolean = {
                repository.minstEnKandidatHarFåttForespørsel(
                    forespørselOmDelingAvCvDto.stillingsId.toUUID(),
                    forespørselOmDelingAvCvDto.aktorIder
                )
            }

            val stilling = hentStilling(forespørselOmDelingAvCvDto.stillingsId.toUUID())

            val (kanSende, statuskode, feilmelding) = kanSendeForespørsel(
                stilling,
                minstEnKandidatHarFåttForespørselFør
            )

            if (!kanSende) {
                loggFeilMedStilling(feilmelding, forespørselOmDelingAvCvDto.stillingsId)
                ctx.status(statuskode).json(feilmelding)
            } else {
                repository.lagreUsendteForespørsler(
                    aktørIder = forespørselOmDelingAvCvDto.aktorIder,
                    stillingsId = forespørselOmDelingAvCvDto.stillingsId.toUUID(),
                    svarfrist = forespørselOmDelingAvCvDto.svarfrist.toLocalDateTime(),
                    deltAvNavIdent = ctx.hentNavIdent(),
                    navKontor = forespørselOmDelingAvCvDto.navKontor,
                    callId = ctx.hentCallId()
                )

                ctx.json(hentForespørslerGruppertPåAktørId(forespørselOmDelingAvCvDto.stillingsId))
                ctx.status(201)
                sendUsendteForespørsler()
            }
        }
    }

    private fun kanSendeForespørsel(
        stilling: Stilling?,
        minstEnKandidatHarFåttForespørselFør: () -> Boolean
    ): Triple<Boolean, Int, String> =
        if (stilling == null) {
            Triple(false, 404, "Stillingen eksisterer ikke")
        } else if (stilling.kanIkkeDelesMedKandidaten) {
            Triple(false, 400, "Stillingen kan ikke deles med brukeren pga. stillingskategori.")
        } else if (minstEnKandidatHarFåttForespørselFør()) {
            Triple(false, 409, "Minst én kandidat har fått forespørselen fra før.")
        } else {
            Triple(true, 200, "")
        }

    val resendForespørselOmDelingAvCv: (Context) -> Unit = { ctx ->
        val inboundDto = ctx.bodyAsClass(ResendForespørselInboundDto::class.java)
        val aktørId = ctx.pathParam(aktorIdParamName)

        val kandidatensSisteForespørselForStillingen = repository.hentSisteForespørselForKandidatOgStilling(
            aktørId,
            inboundDto.stillingsId.toUUID()
        )

        val (kanSendeNyForespørsel, feilmelding) = kanResendeForespørsel(kandidatensSisteForespørselForStillingen)

        if (!kanSendeNyForespørsel) {
            loggFeilMedStilling(feilmelding, inboundDto.stillingsId)
            ctx.status(400).json(feilmelding)
        } else {
            repository.lagreUsendteForespørsler(
                aktørIder = listOf(aktørId),
                stillingsId = inboundDto.stillingsId.toUUID(),
                svarfrist = inboundDto.svarfrist.toLocalDateTime(),
                deltAvNavIdent = ctx.hentNavIdent(),
                navKontor = inboundDto.navKontor,
                callId = ctx.hentCallId(),
            )

            ctx.json(hentForespørslerGruppertPåAktørId(inboundDto.stillingsId))
            ctx.status(201)

            sendUsendteForespørsler()
        }
    }

    private fun kanResendeForespørsel(sisteForespørselForKandidatOgStilling: Forespørsel?): Pair<Boolean, String> =
        if (sisteForespørselForKandidatOgStilling == null) {
            Pair(false, "Kan ikke resende forespørsel fordi kandidaten ikke har fått forespørsel før")
        } else if (sisteForespørselForKandidatOgStilling.harSvartJa()) {
            Pair(false, "Kan ikke resende forespørsel fordi kandidaten allerede har svart ja")
        } else if (sisteForespørselForKandidatOgStilling.venterPåSvar()) {
            Pair(false, "Kan ikke resende forespørsel fordi kandidaten ennå ikke har besvart en aktiv forespørsel")
        } else if (sisteForespørselForKandidatOgStilling.kanIkkeVarsleBruker()) {
            Pair(false, "Kan ikke resende forespørsel fordi forrige forespørsel ikke kunne sendes til kandidat")
        } else {
            Pair(true, "")
        }

    private fun hentForespørslerGruppertPåAktørId(stillingsId: String) =
        repository.hentForespørsler(stillingsId.toUUID())
            .map { it.tilOutboundDto() }
            .groupBy { it.aktørId }

    private fun loggFeilMedStilling(feilmelding: String, stillingsId: String) =
        log.warn("$feilmelding: Stillingsid: $stillingsId")
}

data class ForespørselInboundDto(
    val stillingsId: String,
    val svarfrist: ZonedDateTime,
    val aktorIder: List<String>,
    val navKontor: String
)

data class ResendForespørselInboundDto(
    val stillingsId: String,
    val svarfrist: ZonedDateTime,
    val navKontor: String
)

data class ForespørselOutboundDto(
    val aktørId: String,
    val stillingsId: String,

    val deltStatus: DeltStatus,
    val deltTidspunkt: LocalDateTime,
    val deltAv: String,
    val svarfrist: ZonedDateTime,

    val tilstand: Tilstand?,
    val svar: Svar?,
    val begrunnelseForAtAktivitetIkkeBleOpprettet: BegrunnelseForAtAktivitetIkkeBleOpprettet?,
    val navKontor: String?
)

typealias ForespørslerGruppertPåAktørId = Map<String, List<ForespørselOutboundDto>>
