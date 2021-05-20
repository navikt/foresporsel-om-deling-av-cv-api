import io.javalin.http.Context
import utils.log

val validerToken: (Context) -> Unit = { ctx ->
    log("validerToken()").info("Validerer token")

    val url = ctx.req.requestURL.toString()
    val skalValidereToken = !endepunktUtenTokenvalidering.contains(url)

    if (skalValidereToken) {



    }

}


const val baseUrl = "http://localhost:8333"
val endepunktUtenTokenvalidering = listOf(
    "$baseUrl/internal/isAlive",
    "$baseUrl/internal/isReady"
)
