import io.javalin.http.Context
import utils.hentCallId
import utils.log
import java.util.*

class Service(repository: Repository) {

    val lagreForespørselOmDelingAvCv: (Context) -> Unit = { ctx ->
        log.info("lagre forespørsel")

        val forespørselOmDelingAvCvDto = ctx.bodyAsClass(ForespørselOmDelingAvCvInboundDto::class.java)
        repository.lagreUsendteForespørsler(
            forespørselOmDelingAvCvDto.aktorIder,
            UUID.fromString(forespørselOmDelingAvCvDto.stillingsId),
            "veileder", // TODO
            ctx.hentCallId()
        )

        ctx.status(201)
    }
}

data class ForespørselOmDelingAvCvInboundDto(
    val stillingsId: String,
    val aktorIder: List<String>,
)
