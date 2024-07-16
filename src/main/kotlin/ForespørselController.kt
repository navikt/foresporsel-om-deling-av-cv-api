import auth.Autorisasjon
import auth.TokenHandler
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.javalin.http.Context
import org.slf4j.event.Level
import org.slf4j.event.Level.*
import stilling.Stilling
import utils.hentCallId
import utils.log
import utils.toUUID
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.*

const val stillingsIdParamName = "stillingsId"
const val aktorIdParamName = "aktørId"

class ForespørselController(
    private val repository: Repository,
    private val tokenHandler: TokenHandler,
    private val sendUsendteForespørsler: () -> Unit,
    private val hentStilling: (UUID) -> Stilling?,
    private val autorisasjon: Autorisasjon
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
                //autorisasjon.verifiserRoller(tokenHandler.hentRoller(ctx), listOf(Rolle.UTVIKLER,Rolle.JOBBSØKERRETTET, Rolle.ARBEIDSGIVERRETTET))
                autorisasjon.verifiserKandidatTilgang(ctx, tokenHandler.hentNavIdent(ctx), aktørId)
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

            when (val resultat = kanSendeForespørsel(stilling, minstEnKandidatHarFåttForespørselFør)) {
                is Ok -> {
                    repository.lagreUsendteForespørsler(
                        aktørIder = forespørselOmDelingAvCvDto.aktorIder,
                        stillingsId = forespørselOmDelingAvCvDto.stillingsId.toUUID(),
                        svarfrist = forespørselOmDelingAvCvDto.svarfrist.toLocalDateTime(),
                        deltAvNavIdent = tokenHandler.hentNavIdent(ctx),
                        navKontor = forespørselOmDelingAvCvDto.navKontor,
                        callId = ctx.hentCallId()
                    )
                    ctx.json(hentForespørslerGruppertPåAktørId(forespørselOmDelingAvCvDto.stillingsId))
                    ctx.status(201)
                    sendUsendteForespørsler()
                }

                is Feil -> {
                    loggFeilMedStilling(
                        resultat.feilmelding,
                        forespørselOmDelingAvCvDto.stillingsId,
                        resultat.loggLevel
                    )
                    ctx.status(resultat.httpResponsStatusKode).json(resultat.feilmelding)
                }
            }
        }
    }

    private fun kanSendeForespørsel(
        stilling: Stilling?,
        minstEnKandidatHarFåttForespørselFør: () -> Boolean
    ): Resultat =
        if (stilling == null) {
            Feil("Stillingen eksisterer ikke", 404, WARN)
        } else if (stilling.kanIkkeDelesMedKandidaten) {
            Feil("Stillingen kan ikke deles med brukeren pga. stillingskategori.", 400, WARN)
        } else if (minstEnKandidatHarFåttForespørselFør()) {
            Feil("Minst én kandidat har fått forespørselen fra før.", 409, INFO)
        } else Ok

    val resendForespørselOmDelingAvCv: (Context) -> Unit = { ctx ->
        val inboundDto = ctx.bodyAsClass(ResendForespørselInboundDto::class.java)
        val aktørId = ctx.pathParam(aktorIdParamName)

        val kandidatensSisteForespørselForStillingen = repository.hentSisteForespørselForKandidatOgStilling(
            aktørId,
            inboundDto.stillingsId.toUUID()
        )

        when (val resultat = kanResendeForespørsel(kandidatensSisteForespørselForStillingen)) {
            is Ok -> {
                repository.lagreUsendteForespørsler(
                    aktørIder = listOf(aktørId),
                    stillingsId = inboundDto.stillingsId.toUUID(),
                    svarfrist = inboundDto.svarfrist.toLocalDateTime(),
                    deltAvNavIdent = tokenHandler.hentNavIdent(ctx),
                    navKontor = inboundDto.navKontor,
                    callId = ctx.hentCallId(),
                )
                ctx.json(hentForespørslerGruppertPåAktørId(inboundDto.stillingsId))
                ctx.status(201)
                sendUsendteForespørsler()
            }

            is Feil -> {
                loggFeilMedStilling(resultat.feilmelding, inboundDto.stillingsId, WARN)
                ctx.status(resultat.httpResponsStatusKode).json(resultat.feilmelding)
            }
        }
    }

    private fun kanResendeForespørsel(sisteForespørselForKandidatOgStilling: Forespørsel?): Resultat =
        if (sisteForespørselForKandidatOgStilling == null) {
            Feil("Kan ikke resende forespørsel fordi kandidaten ikke har fått forespørsel før", 400, WARN)
        } else if (sisteForespørselForKandidatOgStilling.harSvartJa()) {
            Feil("Kan ikke resende forespørsel fordi kandidaten allerede har svart ja", 400, WARN)
        } else if (sisteForespørselForKandidatOgStilling.venterPåSvar()) {
            Feil(
                "Kan ikke resende forespørsel fordi kandidaten ennå ikke har besvart en aktiv forespørsel",
                400,
                WARN
            )
        } else Ok

    private fun hentForespørslerGruppertPåAktørId(stillingsId: String) =
        repository.hentForespørsler(stillingsId.toUUID())
            .map { it.tilOutboundDto() }
            .groupBy { it.aktørId }

    private fun loggFeilMedStilling(feilmelding: String, stillingsId: String, loggLevel: Level) {
        val msg = feilmelding.dropLastWhile { it == '.' || it == ':' } + ". StillingsId: $stillingsId"
        when (loggLevel) {
            ERROR -> log.error(msg)
            WARN -> log.warn(msg)
            INFO -> log.info(msg)
            DEBUG -> log.debug(msg)
            TRACE -> log.trace(msg)
        }
    }
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

private sealed interface Resultat
private object Ok : Resultat
private data class Feil(val feilmelding: String, val httpResponsStatusKode: Int, val loggLevel: Level) : Resultat
