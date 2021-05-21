import io.javalin.http.Context
import utils.log
import java.time.LocalDateTime
import javax.sql.DataSource

class Service(repository: Repository) {

    val lagreForespørselOmDelingAvCv: (Context) -> Unit = { ctx ->
        log.info("lagre forespørsel")

        val forespørselOmDelingAvCvDto = ctx.bodyAsClass(ForespørselOmDelingAvCvInboundDto::class.java)

        val nå = LocalDateTime.now()

        val forespørselOmDelingAvCver = forespørselOmDelingAvCvDto.aktorIder.map {
            ForespørselOmDelingAvCv(
                aktørId = it,
                stillingsId = forespørselOmDelingAvCvDto.stillingsId,
                deltStatus = DeltStatus.IKKE_SENDT,
                deltTidspunkt = nå,
                deltAv = "veileder", // TODO
                svar = Svar.IKKE_SVART,
                svarTidspunkt = null
            )
        }

        repository.lagreBatch(forespørselOmDelingAvCver)

        ctx.status(201)
    }
}

data class ForespørselOmDelingAvCvInboundDto(
    val stillingsId: String,
    val aktorIder: List<String>,
)