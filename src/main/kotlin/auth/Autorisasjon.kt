package auth

import auth.obo.KandidatsokApiKlient
import io.javalin.http.Context
import io.javalin.http.HttpResponseException
import org.eclipse.jetty.http.HttpStatus

class Autorisasjon(private val kandidatsokApiKlient: KandidatsokApiKlient) {
    fun validerRoller(rollerIToken: List<TokenHandler.Rolle>, nødvendigeRoller: List<TokenHandler.Rolle>) {
        if (rollerIToken.none { it in nødvendigeRoller }) {
            throw HttpResponseException(HttpStatus.FORBIDDEN_403, "Ikke tilgang")
        }
    }

    fun validerKandidatTilgang(ctx: Context, navIdent: String, aktorid: String) {
        kandidatsokApiKlient.verifiserKandidatTilgang(ctx, navIdent, aktorid)
    }

}