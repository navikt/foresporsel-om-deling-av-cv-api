import com.github.kittinunf.fuel.core.Request
import no.nav.security.mock.oauth2.MockOAuth2Server

fun Request.medVeilederCookie(mockOAuth2Server: MockOAuth2Server): Request {
    return this.header("Cookie", "isso-idtoken=${mockOAuth2Server.issueToken().serialize()}")
}
