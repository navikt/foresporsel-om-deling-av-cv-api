import io.javalin.http.Context
import stilling.Stilling
import utils.hentCallId
import utils.log
import utils.toUUID
import java.time.LocalDateTime
import java.util.UUID

const val stillingsIdParamName = "stillingsId"
const val aktorIdParamName = "aktørId"

class Controller(repository: Repository, sendUsendteForespørsler: () -> Unit, hentStilling: (UUID) -> Stilling?) {

    val hentForespørsler: (Context) -> Unit = { ctx ->
        try {
            ctx.pathParam(stillingsIdParamName).toUUID()
        } catch (exception: IllegalArgumentException) {
            ctx.status(400)
            null
        }?.let { stillingsId ->
            val outboundDto = repository.hentForespørsler(stillingsId).map(Forespørsel::tilOutboundDto)

            ctx.json(outboundDto)
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
            val outboundDto = repository.hentForespørslerForKandidat(aktørId).map(Forespørsel::tilOutboundDto)

            ctx.json(outboundDto)
            ctx.status(200)
        }
    }

    val lagreForespørselOmDelingAvCv: (Context) -> Unit = { ctx ->
        val forespørselOmDelingAvCvDto = ctx.bodyAsClass(ForespørselInboundDto::class.java)

        fun minstEnKandidatHarFåttForespørsel() = repository.minstEnKandidatHarFåttForespørsel(
            forespørselOmDelingAvCvDto.stillingsId.toUUID(),
            forespørselOmDelingAvCvDto.aktorIder
        )

        val stilling = hentStilling(forespørselOmDelingAvCvDto.stillingsId.toUUID())

        if (stilling == null) {
            val feilmelding = "Stillingen eksisterer ikke"
            loggFeilMedStilling(feilmelding, forespørselOmDelingAvCvDto.stillingsId)

            ctx.status(404).json(feilmelding)
        } else if (stilling.kanIkkeDelesMedKandidaten) {
            val feilmelding = "Stillingen kan ikke deles med brukeren pga. stillingskategori."
            loggFeilMedStilling(feilmelding, forespørselOmDelingAvCvDto.stillingsId)

            ctx.status(400).json(feilmelding)
        } else if (minstEnKandidatHarFåttForespørsel()) {
            val feilmelding = "Minst én kandidat har fått forespørselen fra før."
            loggFeilMedStilling(feilmelding, forespørselOmDelingAvCvDto.stillingsId)

            ctx.status(409).json(feilmelding)
        } else {
            repository.lagreUsendteForespørsler(
                aktørIder = forespørselOmDelingAvCvDto.aktorIder,
                stillingsId = forespørselOmDelingAvCvDto.stillingsId.toUUID(),
                svarfrist = forespørselOmDelingAvCvDto.svarfrist,
                deltAvNavIdent = ctx.hentNavIdent(),
                callId = ctx.hentCallId()
            )

            val alleForespørslerPåStilling =
                repository.hentForespørsler(forespørselOmDelingAvCvDto.stillingsId.toUUID())
                    .map { it.tilOutboundDto() }

            ctx.json(alleForespørslerPåStilling)
            ctx.status(201)

            sendUsendteForespørsler()
        }
    }

    val resendForespørselOmDelingAvCv: (Context) -> Unit = { ctx ->
        val inboundDto = ctx.bodyAsClass(ResendForespørselInboundDto::class.java)
        val aktørId = ctx.pathParam(aktorIdParamName)

        val kandidatensSisteForespørselForStillingen = repository.hentSisteForespørselForKandidatOgStilling(
            aktørId,
            inboundDto.stillingsId.toUUID()
        )

        val kanSendeNyForespørsel = kanResendeForespørsel(kandidatensSisteForespørselForStillingen)

        if (!kanSendeNyForespørsel.first) {
            loggFeilMedStilling(kanSendeNyForespørsel.second, inboundDto.stillingsId)
            ctx.status(400).json(kanSendeNyForespørsel.second)
        } else {
            repository.lagreUsendteForespørsler(
                aktørIder = listOf(aktørId),
                stillingsId = inboundDto.stillingsId.toUUID(),
                svarfrist = inboundDto.svarfrist,
                deltAvNavIdent = ctx.hentNavIdent(),
                callId = ctx.hentCallId()
            )

            val alleForespørslerPåStilling =
                repository.hentForespørsler(inboundDto.stillingsId.toUUID())
                    .map { it.tilOutboundDto() }

            ctx.json(alleForespørslerPåStilling)
            ctx.status(201)

            sendUsendteForespørsler()
        }
    }

    private fun loggFeilMedStilling(feilmelding: String, stillingsId: String) =
        log.warn("$feilmelding: Stillingsid: $stillingsId")

    private fun kanResendeForespørsel(sisteForespørselForKandidatOgStilling: Forespørsel?): Pair<Boolean, String> {
        return if (sisteForespørselForKandidatOgStilling == null) {
            Pair(false, "Kan ikke resende forespørsel fordi kandidaten ikke har fått forespørsel før")
        } else if (sisteForespørselForKandidatOgStilling.svar?.harSvartJa == true) {
            Pair(false, "Kan ikke resende forespørsel fordi kandidaten allerede har svart ja")
        } else if (sisteForespørselForKandidatOgStilling.tilstand == Tilstand.HAR_VARSLET || sisteForespørselForKandidatOgStilling.tilstand == Tilstand.PROVER_VARSLING) {
            Pair(false, "Kan ikke resende forespørsel fordi kandidaten ennå ikke har besvart en aktiv forespørsel")
        } else if (sisteForespørselForKandidatOgStilling.tilstand == Tilstand.KAN_IKKE_VARSLE) {
            Pair(false, "Kan ikke resende forespørsel fordi...") // TODO: Vil en tilstand KAN_IKKE_VARSLE bli AVBRUTT når fristen går ut?
        } else {
            Pair(true, "")
        }
    }
}

data class ForespørselInboundDto(
    val stillingsId: String,
    val svarfrist: LocalDateTime,
    val aktorIder: List<String>,
)

data class ResendForespørselInboundDto(
    val stillingsId: String,
    val svarfrist: LocalDateTime
)

data class ForespørselOutboundDto(
    val aktørId: String,
    val stillingsId: String,

    val deltStatus: DeltStatus,
    val deltTidspunkt: LocalDateTime,
    val deltAv: String,
    val svarfrist: LocalDateTime,

    val tilstand: Tilstand?,
    val svar: Svar?,
    val begrunnelseForAtAktivitetIkkeBleOpprettet: BegrunnelseForAtAktivitetIkkeBleOpprettet?
)
