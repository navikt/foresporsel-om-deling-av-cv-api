import io.javalin.http.Context
import sendforespørsel.UsendtScheduler
import utils.hentCallId
import utils.toUUID
import java.time.LocalDateTime
import java.util.*

const val stillingsIdParamName = "stillingsId"

class Controller(repository: Repository, sendUsendteForespørsler: () -> Unit) {

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

    val lagreForespørselOmDelingAvCv: (Context) -> Unit = { ctx ->
        val forespørselOmDelingAvCvDto = ctx.bodyAsClass(ForespørselInboundDto::class.java)

        val minstEnKandidatHarFåttForespørsel = repository.minstEnKandidatHarFåttForespørsel(
            forespørselOmDelingAvCvDto.stillingsId.toUUID(),
            forespørselOmDelingAvCvDto.aktorIder
        )

        if (minstEnKandidatHarFåttForespørsel) {
            ctx.status(409)
            ctx.json("Minst én kandidat har fått forespørselen fra før")
        } else {
            repository.lagreUsendteForespørsler(
                aktørIder = forespørselOmDelingAvCvDto.aktorIder,
                stillingsId = forespørselOmDelingAvCvDto.stillingsId.toUUID(),
                svarfrist = forespørselOmDelingAvCvDto.svarfrist,
                deltAvNavIdent = ctx.hentNavIdent(),
                callId = ctx.hentCallId()
            )

            val alleForespørslerPåStilling = repository.hentForespørsler(forespørselOmDelingAvCvDto.stillingsId.toUUID())
                .map { it.tilOutboundDto() }

            ctx.json(alleForespørslerPåStilling)
            ctx.status(201)

            sendUsendteForespørsler()
        }
    }
}

data class ForespørselInboundDto(
    val stillingsId: String,
    val svarfrist: LocalDateTime,
    val aktorIder: List<String>,
)

data class ForespørselOutboundDto(
    val aktørId: String,

    val deltStatus: DeltStatus,
    val deltTidspunkt: LocalDateTime,
    val deltAv: String,
    val svarfrist: LocalDateTime,

    val tilstand: Tilstand?,
    val svar: Svar?,
    val begrunnelseForAtAktivitetIkkeBleOpprettet: BegrunnelseForAtAktivitetIkkeBleOpprettet?
)
