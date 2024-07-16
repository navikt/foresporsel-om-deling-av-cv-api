package auth

import auth.obo.KandidatsokApiKlient
import io.javalin.http.HttpResponseException
import org.eclipse.jetty.http.HttpStatus

class Autorisasjon(private val kandidatsokApiKlient: KandidatsokApiKlient) {
    fun verifiserRoller(rollerIToken: List<TokenHandler.Rolle>, nødvendigeRoller: List<TokenHandler.Rolle>) {
        if (rollerIToken.none { it in nødvendigeRoller }) {
            throw HttpResponseException(HttpStatus.FORBIDDEN_403, "Ikke tilgang")
        }
    }

    fun verifiserKandidatTilgang(navIdent: String,  aktorid: String) {
        kandidatsokApiKlient.verifiserKandidatTilgang(navIdent, aktorid)
    }

}