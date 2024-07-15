package auth

import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.context.TokenValidationContextHolder

class SimpleTokenValidationContextHolder : TokenValidationContextHolder {
    private var context: TokenValidationContext? = null

    override fun getTokenValidationContext(): TokenValidationContext {
        return context ?: throw IllegalStateException("TokenValidationContext is not set")
    }

    override fun setTokenValidationContext(tokenValidationContext: TokenValidationContext) {
        this.context = tokenValidationContext
    }
}