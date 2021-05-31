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

val validerToken: (IssuerProperties) -> (Context) -> Unit = { issuerProperties ->
    { ctx ->
        val url = ctx.req.requestURL.toString()

        if (url.skalValideres()) {
            val validerteTokens = hentValiderteTokens(ctx, issuerProperties)

            if (validerteTokens.hasValidToken()) {
                val navIdent = validerteTokens
                    .getClaims(issuerProperties.cookieName)
                    .getStringClaim(navIdentClaimKey)

                ctx.attribute(navIdentAttributeKey, navIdent)

            } else {
                throw UnauthorizedResponse()
            }
        }
    }
}

fun Context.hentNavIdent(): String {
    return attribute(navIdentAttributeKey)!!
}

private fun hentValiderteTokens(ctx: Context, issuerProperties: IssuerProperties): TokenValidationContext {
    val cookieName = issuerProperties.cookieName
    val tokenValidationHandler = JwtTokenValidationHandler(
        MultiIssuerConfiguration(mapOf(Pair(cookieName, issuerProperties)))
    )

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