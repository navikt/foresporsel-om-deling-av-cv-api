import io.javalin.http.Context
import mottasvar.Svar
import utils.hentCallId
import java.time.LocalDateTime
import java.util.*

const val stillingsIdParamName = "stillingsId"

class Controller(repository: Repository) {

    val hentForespørsler: (Context) -> Unit = { ctx ->
        try {
            UUID.fromString(ctx.pathParam(stillingsIdParamName))
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
        val forespørselId = UUID.randomUUID()

        repository.lagreUsendteForespørsler(
            aktørIder = forespørselOmDelingAvCvDto.aktorIder,
            stillingsId = UUID.fromString(forespørselOmDelingAvCvDto.stillingsId),
            forespørselId = forespørselId,
            svarfrist = forespørselOmDelingAvCvDto.svarfrist,
            deltAvNavIdent = ctx.hentNavIdent(),
            callId = ctx.hentCallId()
        )

        ctx.json("")
        ctx.status(201)
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

    val svar: Svar,
    val svarTidspunkt: LocalDateTime?,
)
