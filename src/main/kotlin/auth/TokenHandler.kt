package auth

import Rollekeys
import io.javalin.http.Context
import io.javalin.http.UnauthorizedResponse
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.http.HttpRequest
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import utils.log
import java.time.LocalDateTime

class TokenHandler(
    private val issuerProperties: List<IssuerProperties>,
    rolleKeys: Rollekeys
) {
    private val endepunktUtenTokenvalidering = listOf("/internal/isAlive", "/internal/isReady")
    private val navIdentClaimKey = "NAVident"
    private val rolleClaimKey = "groups"
    private val navIdentAttributeKey = "navIdent"

    private var cachedHandler: CachedHandler? = null

    init {
        Rolle.setRolleKeys(rolleKeys)
    }

    fun validerToken(ctx: Context) {
        val url = ctx.req().requestURL.toString()
        if (endepunktUtenTokenvalidering.none { url.contains(it) }) {
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

    fun hentValiderteTokens(ctx: Context): TokenValidationContext {
        val tokenValidationHandler = hentTokenValidationHandler()
        val validatedTokens = tokenValidationHandler.getValidatedTokens(getHttpRequest(ctx))
        if (validatedTokens == null) {
            throw RuntimeException("Token validation failed")
        }
        return validatedTokens
    }

    private fun hentNavIdent(validerteTokens: TokenValidationContext): String {
        return issuerProperties.mapNotNull { issuerProperty ->
            validerteTokens.getClaims(issuerProperty.cookieName)?.getStringClaim(navIdentClaimKey)
        }.first()
    }

    fun hentRoller(ctx: Context): List<Rolle> {
        return issuerProperties.flatMap { issuerProperty ->
            hentValiderteTokens(ctx)
                .getClaims(issuerProperty.cookieName)
                ?.getAsList(rolleClaimKey)
                ?.mapNotNull { Rolle.fromClaim(it) }
                ?: emptyList()
        }
    }

    private fun hentTokenValidationHandler(): JwtTokenValidationHandler {
        return if (cachedHandler == null || cachedHandler!!.erUtgått()) {
            val expires = LocalDateTime.now().plusHours(1)
            log.info("Henter og cacher nye public keys til $expires")

            val newHandler = JwtTokenValidationHandler(
                MultiIssuerConfiguration(issuerProperties.associateBy { it.cookieName })
            )

            cachedHandler = CachedHandler(newHandler, expires)
            newHandler
        } else {
            cachedHandler!!.handler
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

    class CachedHandler(
        val handler: JwtTokenValidationHandler,
        val expires: LocalDateTime) {

        fun erUtgått() = expires.isBefore(LocalDateTime.now())
    }

    enum class Rolle {
        JOBBSØKERRETTET,
        ARBEIDSGIVERRETTET,
        UTVIKLER;

        companion object {
            private lateinit var rollekeys: Rollekeys

            fun setRolleKeys(rolleKeys: Rollekeys) {
                rollekeys = rolleKeys
            }

            fun fromClaim(value: String): Rolle? {
                return when (value) {
                    rollekeys.jobbsokerrettetGruppe -> JOBBSØKERRETTET
                    rollekeys.arbeidsgiverrettetGruppe -> ARBEIDSGIVERRETTET
                    rollekeys.utviklerGruppe -> UTVIKLER
                    else -> null
                }
            }
        }

        fun asString(): String = name.lowercase()
    }
}
