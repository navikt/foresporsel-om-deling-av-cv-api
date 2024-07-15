package auth


import no.nav.security.token.support.core.context.TokenValidationContextHolder

class TokenService(private val tokenValidationContextHolder: TokenValidationContextHolder) {
    private val TOKEN_ISSUER_AZUREAD = "azuread"

    fun hentTokenSomString(): String {
        return tokenValidationContextHolder.tokenValidationContext.getJwtToken(TOKEN_ISSUER_AZUREAD)?.tokenAsString
            ?: throw RuntimeException("Ingen gyldig token funnet for issuer: $TOKEN_ISSUER_AZUREAD")
    }
}