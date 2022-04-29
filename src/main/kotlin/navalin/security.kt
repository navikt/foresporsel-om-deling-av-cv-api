package navalin

import io.javalin.core.security.AccessManager
import io.javalin.core.security.RouteRole
import io.javalin.http.Context
import io.javalin.http.Handler

fun manageAccess(roleConfigs: List<RoleConfig>) =
    AccessManager { handler: Handler, ctx: Context, roles: Set<RouteRole ->

        val allRoles = roleConfigs.map { it.role }

        if


    }


/*

fun styrTilgang(issuerProperties: Map<Rolle, IssuerProperties>) =
    AccessManager { handler: Handler, ctx: Context, roller: Set<RouteRole> ->

        val erAutentisert =
            if (roller.contains(Rolle.VEILEDER)) {
                autentiserVeileder(hentTokenClaims(ctx, issuerProperties[Rolle.VEILEDER]!!))
            } else if (roller.contains(Rolle.ARBEIDSGIVER)) {
                autentiserArbeidsgiver(hentTokenClaims(ctx, issuerProperties[Rolle.ARBEIDSGIVER]!!))
            } else false

        if (erAutentisert) {
            handler.handle(ctx)
        } else {
            throw ForbiddenResponse()
        }
    }


fun interface Autentiseringsmetode {
    operator fun invoke(claims: JwtTokenClaims?): Boolean
}

val autentiserVeileder = Autentiseringsmetode { it?.get("NAVident")?.toString()?.isNotEmpty() ?: false }
val autentiserArbeidsgiver = Autentiseringsmetode { it != null }


private fun hentTokenClaims(ctx: Context, issuerProperties: IssuerProperties) =
    lagTokenValidationHandler(issuerProperties)
        .getValidatedTokens(ctx.httpRequest)
        .anyValidClaims.orElseGet { null }

private fun lagTokenValidationHandler(issuerProperties: IssuerProperties) =
    JwtTokenValidationHandler(
        MultiIssuerConfiguration(mapOf(issuerProperties.cookieName to issuerProperties))
    )

private val Context.httpRequest: HttpRequest
    get() = object : HttpRequest {
        override fun getHeader(headerName: String?) = headerMap()[headerName]
        override fun getCookies() = cookieMap().map { (name, value) ->
            object : HttpRequest.NameValue {
                override fun getName() = name
                override fun getValue() = value
            }
        }.toTypedArray()
    }




 */