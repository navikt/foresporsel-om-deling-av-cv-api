import auth.hentTokenValidationHandler
import io.javalin.http.Context
import io.javalin.http.UnauthorizedResponse
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.http.HttpRequest
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler

private val endepunktUtenTokenvalidering = listOf(
    "/internal/isAlive",
    "/internal/isReady"
)

const val navIdentClaimKey = "NAVident"
const val navIdentAttributeKey = "navIdent"

private val skalValideres: String.() -> Boolean = { endepunktUtenTokenvalidering.none(this::contains) }

val validerToken: (List<IssuerProperties>) -> (Context) -> Unit = { issuerProperties ->
    { ctx ->
        val url = ctx.req.requestURL.toString()

        if (url.skalValideres()) {
            val validerteTokens = hentValiderteTokens(ctx, issuerProperties)

            if (validerteTokens.hasValidToken()) {
                val navIdent = hentNavIdent(validerteTokens, issuerProperties)

                ctx.attribute(navIdentAttributeKey, navIdent)

            } else {
                throw UnauthorizedResponse()
            }
        }
    }
}

private fun hentNavIdent(
    validerteTokens: TokenValidationContext,
    listOfIssuerProperties: List<IssuerProperties>
) = listOfIssuerProperties.mapNotNull { issuerProperties ->
    validerteTokens
        .getClaims(issuerProperties.cookieName)
        ?.getStringClaim(navIdentClaimKey)
}.first()

fun Context.hentNavIdent(): String {
    return attribute(navIdentAttributeKey)!!
}

private fun hentValiderteTokens(ctx: Context, issuerProperties: List<IssuerProperties>): TokenValidationContext {
    val tokenValidationHandler = hentTokenValidationHandler(issuerProperties)

    return tokenValidationHandler.getValidatedTokens(getHttpRequest(ctx))
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
