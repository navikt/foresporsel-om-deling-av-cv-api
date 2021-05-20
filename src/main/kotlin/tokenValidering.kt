import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.http.HttpRequest
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import utils.log

const val baseUrl = "http://localhost:8333"
val endepunktUtenTokenvalidering = listOf(
    "$baseUrl/internal/isAlive",
    "$baseUrl/internal/isReady"
)

val validerToken: (IssuerProperties) -> (Context) -> Unit = { issuerProperties ->
    { ctx ->
        log("validerToken()").info("Validerer token")

        val url = ctx.req.requestURL.toString()
        val skalValidereToken = !endepunktUtenTokenvalidering.contains(url)

        if (skalValidereToken) {
            val claims = hentClaimsFraValidertToken(ctx, issuerProperties)

            if (!tokenErGyldig(claims)) {
                throw ForbiddenResponse()
            }
        }
    }
}

fun hentClaimsFraValidertToken(ctx: Context, issuerProperties: IssuerProperties): JwtTokenClaims {
    val cookieName = issuerProperties.cookieName
    val tokenValidationHandler =
        JwtTokenValidationHandler(
            MultiIssuerConfiguration(mapOf(Pair(cookieName, issuerProperties)))
        )

    val tokenValidationContext = tokenValidationHandler.getValidatedTokens(getHttpRequest(ctx))
    return tokenValidationContext.getClaims(cookieName)
}

fun tokenErGyldig(claims: JwtTokenClaims?): Boolean {
    if (claims == null || claims["NAVident"] == null) return false
    return claims["NAVident"].toString().isNotEmpty()
}

private fun getHttpRequest(context: Context): HttpRequest = object : HttpRequest {
    override fun getHeader(headerName: String?) = context.headerMap()[headerName]
    override fun getCookies() = context.cookieMap().map { (name, value) ->
        object : HttpRequest.NameValue {
            override fun getName() = name
            override fun getValue() = value
        }
    }.toTypedArray()
}
