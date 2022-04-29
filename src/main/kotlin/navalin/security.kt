package navalin

import io.javalin.core.security.AccessManager
import io.javalin.core.security.RouteRole
import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import io.javalin.http.Handler
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.http.HttpRequest
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler

fun manageAccess(roleConfigs: List<RoleConfig>) =
    AccessManager { handler: Handler, ctx: Context, roles: Set<RouteRole> ->

        if (roles.isEmpty()) {
            throw RuntimeException("Ikke tillatt med endepunkt uten spesifisert rolle")
        }

        val permittedRoleConfigs = roleConfigs.filter { roles.contains(it.role) }
        val permittedIssuerProperties = permittedRoleConfigs.flatMap { it.issuerProperties }
        val tokenClaims = getTokenClaims(ctx, permittedIssuerProperties)

        val authenticated = permittedRoleConfigs.any {
            it.necessaryTokenClaims.all { tokenClaim ->
                tokenClaims[tokenClaim] != null
            }
        }

        if (authenticated) {
            handler.handle(ctx)
        } else {
            throw ForbiddenResponse()
        }
    }

private fun getTokenClaims(ctx: Context, issuerProperties: List<IssuerProperties>) =
    createTokenValidationHandler(issuerProperties)
        .getValidatedTokens(ctx.httpRequest)
        .anyValidClaims.orElseGet { null }

private fun createTokenValidationHandler(issuerProperties: List<IssuerProperties>) =
    JwtTokenValidationHandler(
        MultiIssuerConfiguration(
            issuerProperties.associateBy { it.cookieName }
        )
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