import io.javalin.http.Context
import java.time.LocalDate

class SvarstatistikkController(private val repository: Repository) {
    val hentSvarstatistikk: (Context) -> Unit = { ctx ->
        val fraOgMed = ctx.queryParam("fraOgMed")
        val tilOgMed = ctx.queryParam("tilOgMed")
        val navKontor = ctx.queryParam("navKontor")

        if (fraOgMed == null || tilOgMed == null || navKontor == null) {
            ctx.status(400).json("Du mangler én eller flere query-params, trenger fraOgMed, tilOgMed og navKontor")
        } else {
            val forespørsler: List<Forespørsel> = repository.hentForespørsler(
                LocalDate.parse(fraOgMed).atStartOfDay(),
                LocalDate.parse(tilOgMed).plusDays(1).atStartOfDay(),
                navKontor
            )

            val svartJa = forespørsler.count { it.harSvartJa() }
            val svartNei = forespørsler.count { it.svar != null && !it.harSvartJa() }
            val utløpt = forespørsler.count { it.utløpt() }
            val venterPåSvar = forespørsler.count { it.venterPåSvar() }

            val outboundDto = Svarstatistikk(
                antallSvartJa = svartJa,
                antallSvartNei = svartNei,
                antallUtløpteSvar = utløpt,
                antallVenterPåSvar = venterPåSvar
            )

            ctx.json(outboundDto)
            ctx.status(200)
        }
    }
}

data class Svarstatistikk(
    val antallSvartJa: Number,
    val antallSvartNei: Number,
    val antallVenterPåSvar: Number,
    val antallUtløpteSvar: Number,
)
