package setup

import auth.TokenHandler.Rolle
import com.github.kittinunf.fuel.core.Request
import no.nav.security.mock.oauth2.MockOAuth2Server

fun Request.medToken(mockOAuth2Server: MockOAuth2Server, navIdent: String = "X12345", roller: List<Rolle>): Request {
    return this.header("Authorization", "Bearer ${hentToken(navIdent, roller, mockOAuth2Server)}")
}

fun hentToken(navIdent: String, roller: List<Rolle>, mockOAuth2Server: MockOAuth2Server): String {

    return mockOAuth2Server.issueToken(
        claims = mapOf(
            "NAVident" to navIdent,
            "groups" to roller.map { mapTilId(it) }
        )
    ).serialize()
}

private fun mapTilId(rolle: Rolle): String {
    return when (rolle) {
        Rolle.JOBBSØKERRETTET -> "jobbsokerrettetGruppe"
        Rolle.ARBEIDSGIVERRETTET -> "arbeidsgiverrettetGruppe"
        Rolle.UTVIKLER -> "utviklerGruppe"
    }
}
