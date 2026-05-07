package utils

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.arbeid.dir.utils.retryTemplate
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.time.Duration
import java.util.TimeZone

/**
 * Slår opp fnr eller aktørid mot pam-personoppslag for å finne aktørid eller fnr
 */

open class PersonOppslagKlient(private val personoppslagBaseUrl: String,
                                private val accessToken: () -> String
    ) {
    companion object {
        private val log = LoggerFactory.getLogger(PersonOppslagKlient::class.java)
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .setTimeZone(TimeZone.getTimeZone("Europe/Oslo"))

        val httpClient: HttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .version(HttpClient.Version.HTTP_1_1)
            .build()
    }

    private val url = "${personoppslagBaseUrl}/pam-personoppslag/personidenter/system/oppslag/"


    private fun hentToken() : String {
        return accessToken()
    }

    fun personoppslag(ident: String): PersonIdentResponseDto? {
        try {
            val requestUrl = url + URLEncoder.encode(ident, Charset.forName("UTF-8"))
            val request = HttpRequest.newBuilder()
                .uri(URI(requestUrl))
                .header("Authorization", "Bearer ${hentToken()}")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()

            val response = retryTemplate(logg = log, requestUrl = url) { httpClient.send(request, HttpResponse.BodyHandlers.ofString()) }

            if (response.statusCode() >= 300 || response.body() == null) {
                if (response.statusCode() == 404) {
                    log.warn("Fant ikke ident $ident i pam-personoppslag")
                } else {
                    log.error("Fikk feil fra pam-personoppslag ${response.statusCode()} : ${response.body()}")
                }
                return null
            }

            val responseBody = objectMapper.readValue(response.body(), PersonIdentResponseDto::class.java)
            return responseBody
        } catch (ex: Exception) {
            log.error("Kall til pam-personoppslag feilet: ${ex.message}", ex)
            throw ex
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PersonIdentResponseDto(
    val foedselsnummer: String? = null,
    val aktorId: String? = null,
    val kandidatnummer: String? = null,
    val arenaKandidatnummer: String? = null,
)