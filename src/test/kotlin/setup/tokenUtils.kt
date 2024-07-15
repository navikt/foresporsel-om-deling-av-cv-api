package setup

import com.github.kittinunf.fuel.core.Request
import no.nav.security.mock.oauth2.MockOAuth2Server

fun Request.medVeilederToken(mockOAuth2Server: MockOAuth2Server, navIdent: String = "X12345"): Request {
    return this.header("Authorization", "Bearer ${hentToken(navIdent, mockOAuth2Server)}")
}

fun hentToken(navIdent: String, mockOAuth2Server: MockOAuth2Server): String {

    return mockOAuth2Server.issueToken(
        claims = mapOf(
            "NAVident" to navIdent
        )
    ).serialize()
}
