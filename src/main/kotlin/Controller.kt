import io.javalin.http.Context
import stilling.Stilling
import utils.hentCallId
import utils.log
import utils.toUUID
import java.time.LocalDateTime
import java.util.*

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
            log.warn("Stillingen eksisterer ikke. Stillingsid: ${forespørselOmDelingAvCvDto.stillingsId}")
            ctx.status(404)
            ctx.json("Stillingen eksisterer ikke")
        } else if (stilling.kanIkkeDelesMedKandidaten) {
            log.warn("Stillingen kan ikke deles med brukeren pga. stillingskategori. Stillingsid: ${forespørselOmDelingAvCvDto.stillingsId}")
            ctx.status(400)
            ctx.json("Stillingen kan ikke deles med brukeren pga. stillingskategori.")
        } else if (minstEnKandidatHarFåttForespørsel()) {
            log.warn("Minst én kandidat har fått forespørselen fra før for stillingsid: ${forespørselOmDelingAvCvDto.stillingsId}")
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

            val alleForespørslerPåStilling =
                repository.hentForespørsler(forespørselOmDelingAvCvDto.stillingsId.toUUID())
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
    val stillingsId: String,

    val deltStatus: DeltStatus,
    val deltTidspunkt: LocalDateTime,
    val deltAv: String,
    val svarfrist: LocalDateTime,

    val tilstand: Tilstand?,
    val svar: Svar?,
    val begrunnelseForAtAktivitetIkkeBleOpprettet: BegrunnelseForAtAktivitetIkkeBleOpprettet?
)
