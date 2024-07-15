package auth

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import io.javalin.http.ContentType
import io.javalin.http.HttpResponseException
import org.eclipse.jetty.http.HttpStatus
import org.slf4j.LoggerFactory
import utils.Miljø
import utils.Miljø.*

class KandidatsokApiKlient(private val accessTokenClient: AccessTokenClient) {

    private val logger = LoggerFactory.getLogger(KandidatsokApiKlient::class.java)

    private val kandidatsokUrl = when (Miljø.current) {
        DEV_FSS -> "https://rekrutteringsbistand-kandidatsok-api.intern.dev.nav.no"
        PROD_FSS -> "https://rekrutteringsbistand-kandidatsok-api.intern.nav.no"
        LOKAL -> "http://localhost:9090"
    }

    private val kandidatsokScope = when (Miljø.current) {
        PROD_FSS -> "api://prod-gcp.toi.rekrutteringsbistand-kandidatsok-api/.default"
        DEV_FSS -> "api://dev-gcp.toi.rekrutteringsbistand-kandidatsok-api/.default"
        LOKAL -> ""
    }

    fun verifiserKandidatTilgang(navIdent: String,  aktorid: String) {
        val url = "$kandidatsokUrl/api/brukertilgang"
        val body = BrukertilgangRequestDto(fodselsnummer = null, aktorid = aktorid, kandidatnr = null)
        val token = accessTokenClient.getOboToken(kandidatsokScope, navIdent)

        try {
            val (_, response, result) = Fuel.post(url)
                .header(Headers.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)
                .authentication().bearer(token)
                .jsonBody(body.toJson())
                .response()

            when (result) {
                is Result.Success -> logger.info("Tilgang verifisert: ${response.body().asString("application/json")}")
                is Result.Failure -> handleFailure(response)
            }
        } catch (ex: Exception) {
            logger.error("Kan ikke verifisere tilgang mot bruker, får http 500 fra kandidatsøket", ex)
            throw HttpResponseException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Feil ved verifisering av tilgang")
        }
    }

    private fun handleFailure(response: com.github.kittinunf.fuel.core.Response) {
        when (response.statusCode) {
            404 -> {
                logger.info("Kan ikke verifisere tilgang mot bruker, får http 404 fra kandidatsøket")
                throw HttpResponseException(HttpStatus.NOT_FOUND_404, "Ikke funnet")
            }
            403 -> {
                logger.info("403 Mangler tilgang til persondata")
                throw HttpResponseException(HttpStatus.FORBIDDEN_403, "Forbudt")
            }
            else -> {
                logger.error("Kan ikke verifisere tilgang mot bruker, får http ${response.statusCode} fra kandidatsøket")
                throw HttpResponseException(HttpStatus.INTERNAL_SERVER_ERROR_500, "Feil ved verifisering av tilgang")
            }
        }
    }

    private fun BrukertilgangRequestDto.toJson(): String {
        return """{
            "fodselsnummer": "$fodselsnummer",
            "aktorid": "$aktorid",
            "kandidatnr": "$kandidatnr"
        }"""
    }

    private data class BrukertilgangRequestDto(
        val fodselsnummer: String?,
        val aktorid: String?,
        val kandidatnr: String?
    )
}
