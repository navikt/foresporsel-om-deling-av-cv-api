package auth.obo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import io.javalin.http.Context
import io.javalin.http.HttpResponseException
import org.eclipse.jetty.http.HttpStatus.*
import org.slf4j.LoggerFactory
import utils.Miljø
import utils.Miljø.*

class KandidatsokApiKlient(private val onBehalfOfTokenClient: OnBehalfOfTokenClient) {

    private val logger = LoggerFactory.getLogger(KandidatsokApiKlient::class.java)

    private val kandidatsokUrl = when (Miljø.current) {
        DEV_FSS -> "https://rekrutteringsbistand-kandidatsok-api.intern.dev.nav.no"
        PROD_FSS -> "https://rekrutteringsbistand-kandidatsok-api.intern.nav.no"
        LOKAL -> "http://localhost:9089"
    }

    private val kandidatsokScope = when (Miljø.current) {
        PROD_FSS -> "api://prod-gcp.toi.rekrutteringsbistand-kandidatsok-api/.default"
        DEV_FSS -> "api://dev-gcp.toi.rekrutteringsbistand-kandidatsok-api/.default"
        LOKAL -> ""
    }

    fun verifiserKandidatTilgang(ctx: Context, navIdent: String, aktorid: String) {
        val url = "$kandidatsokUrl/api/brukertilgang"
        val body = BrukertilgangRequestDto(fodselsnummer = null, aktorid = aktorid, kandidatnr = null)
        val token = onBehalfOfTokenClient.getOboToken(ctx, kandidatsokScope, navIdent)

        val (_, response, result) = Fuel.post(url)
            .header(Headers.CONTENT_TYPE, "application/json")
            .authentication().bearer(token)
            .jsonBody(body.toJson())
            .response()

        when (result) {
            is Result.Success -> logger.info("Tilgang verifisert: ${response.body().asString("application/json")}")
            is Result.Failure -> handle(result)
        }
    }

    private fun handle(failure: Result.Failure<FuelError>) {
        when (failure.error.response.statusCode) {
            404 -> {
                logger.info("Kan ikke verifisere tilgang mot bruker, får http 404 fra kandidatsøket", failure.error)
                throw HttpResponseException(NOT_FOUND_404, "Ikke funnet")
            }

            403 -> {
                logger.info("403 Mangler tilgang til persondata", failure.error)
                throw HttpResponseException(FORBIDDEN_403, "Ikke tilgang")
            }

            else -> {
                val msg = "Kan ikke verifisere tilgang mot bruker."
                logger.error(msg, failure.error)
                throw HttpResponseException(INTERNAL_SERVER_ERROR_500, msg)
            }
        }
    }

    private data class BrukertilgangRequestDto(
        val fodselsnummer: String?,
        val aktorid: String?,
        val kandidatnr: String?
    ) {

        fun toJson() =
            jacksonObjectMapper().writeValueAsString(this)
    }
}
