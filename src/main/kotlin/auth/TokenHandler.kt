package auth

import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.http.HttpRequest
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import io.javalin.http.Context
import io.javalin.http.UnauthorizedResponse
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import utils.log
import java.time.LocalDateTime

class TokenHandler(
    private val tokenValidationContextHolder: TokenValidationContextHolder,
    private val issuerProperties: List<IssuerProperties>
) {
    private val TOKEN_ISSUER_AZUREAD = "azuread"
    private val endepunktUtenTokenvalidering = listOf("/internal/isAlive", "/internal/isReady")
    private val navIdentClaimKey = "NAVident"
    private val navIdentAttributeKey = "navIdent"

    private var cachedHandler: CachedHandler? = null

    fun hentTokenSomString(): String {
        return tokenValidationContextHolder.tokenValidationContext.getJwtToken(TOKEN_ISSUER_AZUREAD)?.tokenAsString
            ?: throw RuntimeException("Ingen gyldig token funnet for issuer: $TOKEN_ISSUER_AZUREAD")
    }

    fun validerToken(ctx: Context) {
        val url = ctx.req.requestURL.toString()
        if (skalValideres(url)) {
            val validerteTokens = hentValiderteTokens(ctx)

            if (validerteTokens.hasValidToken()) {
                val navIdent = hentNavIdent(validerteTokens)
                ctx.attribute(navIdentAttributeKey, navIdent)
            } else {
                throw UnauthorizedResponse()
            }
        }
    }

    fun hentNavIdent(ctx: Context): String {
        return ctx.attribute(navIdentAttributeKey) ?: throw UnauthorizedResponse("NAVident ikke funnet i kontekst")
    }

    fun clearCache() {
        cachedHandler = null
    }

    fun setCacheExpires(expires: LocalDateTime) {
        cachedHandler?.expires = expires
    }

    private fun skalValideres(url: String) = endepunktUtenTokenvalidering.none { url.contains(it) }

    private fun hentValiderteTokens(ctx: Context): TokenValidationContext {
        val tokenValidationHandler = hentTokenValidationHandler()
        return tokenValidationHandler.getValidatedTokens(getHttpRequest(ctx))
    }

    private fun hentNavIdent(validerteTokens: TokenValidationContext): String {
        return issuerProperties.mapNotNull { issuerProperty ->
            validerteTokens.getClaims(issuerProperty.cookieName)?.getStringClaim(navIdentClaimKey)
        }.first()
    }

    private fun hentTokenValidationHandler(): JwtTokenValidationHandler {
        return if (cachedHandler != null && cachedHandler!!.expires.isAfter(LocalDateTime.now())) {
            cachedHandler!!.handler
        } else {
            val expires = LocalDateTime.now().plusHours(1)
            log("hentTokenValidationHandler").info("Henter og cacher nye public keys til $expires")

            val newHandler = JwtTokenValidationHandler(
                MultiIssuerConfiguration(issuerProperties.associateBy { it.cookieName })
            )

            cachedHandler = CachedHandler(newHandler, expires)
            newHandler
        }
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

    data class CachedHandler(
        val handler: JwtTokenValidationHandler,
        var expires: LocalDateTime,
    )
}
