package tilgang

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import io.javalin.http.HttpResponseException
import org.eclipse.jetty.http.HttpStatus
import org.slf4j.LoggerFactory
import utils.Miljø
import utils.Miljø.*

class KandidatsokApiKlient(private val accessToken: () -> String) { // TODO: Bytt ut med obo token

    private val logger = LoggerFactory.getLogger(KandidatsokApiKlient::class.java)

    private val kandidatsokUrl = when (Miljø.current) {
        DEV_FSS -> "https://rekrutteringsbistand-kandidatsok-api.intern.dev.nav.no"
        PROD_FSS -> "https://rekrutteringsbistand-kandidatsok-api.intern.nav.no"
        LOKAL -> "http://localhost:9090" //TODO: Sett opp wiremock for å teste lokalt
    }

    fun verifiserTilgang(fodselsnummer: String?, aktorid: String?, kandidatnr: String?) {
        val url = "$kandidatsokUrl/api/brukertilgang"
        val body = BrukertilgangRequestDto(fodselsnummer = fodselsnummer, aktorid = aktorid, kandidatnr = kandidatnr)

        try {
            val (request, response, result) = Fuel.post(url)
                .authentication().bearer(accessToken())
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
