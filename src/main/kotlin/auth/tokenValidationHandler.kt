package auth

import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import utils.log
import java.time.LocalDateTime

data class CachedHandler(
    val handler: JwtTokenValidationHandler,
    val expires: LocalDateTime,
)

var cache: CachedHandler? = null

fun hentTokenValidationHandler(
    issuerProperties: List<IssuerProperties>
): JwtTokenValidationHandler {

    return if (cache != null && cache!!.expires.isAfter(LocalDateTime.now())) {
        cache!!.handler
    } else {
        val expires = LocalDateTime.now().plusHours(1)
        log("hentTokenValidationHandler").info("Henter og cacher nye public keys til $expires")

        val newHandler = JwtTokenValidationHandler(
            MultiIssuerConfiguration(issuerProperties.associateBy { it.cookieName })
        )

        cache = CachedHandler(newHandler, expires);
        newHandler
    }
}