import io.javalin.http.Context
import utils.log
import java.time.LocalDateTime

class Service(repository: Repository) {

    val lagreForespørselOmDelingAvCv: (Context) -> Unit = { ctx ->
        log.info("lagre forespørsel")

        val forespørselOmDelingAvCvDto = ctx.bodyAsClass(ForespørselOmDelingAvCvInboundDto::class.java)
        repository.lagreUsendteForespørsler(
            forespørselOmDelingAvCvDto.aktorIder,
            forespørselOmDelingAvCvDto.stillingsId,
            "veileder" // TODO
        )

        // scheduler.sendUsendteMeldinger()

        ctx.status(201)
    }
}

data class ForespørselOmDelingAvCvInboundDto(
    val stillingsId: String,
    val aktorIder: List<String>,
)