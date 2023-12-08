package stilling

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import utils.Miljø
import utils.Miljø.*
import utils.log
import java.util.*

class StillingKlient(private val accessToken: () -> String) {
    private val stillingssokProxyDokumentUrl = when (Miljø.current) {
        DEV_FSS -> "https://rekrutteringsbistand-stillingssok-proxy.intern.dev.nav.no/stilling/_doc"
        PROD_FSS -> "https://rekrutteringsbistand-stillingssok-proxy.intern.nav.no/stilling/_doc"
        LOKAL -> "http://localhost:9089/stilling/_doc"
    }

    fun hentStilling(uuid: UUID): Stilling? {
        val url = "$stillingssokProxyDokumentUrl/$uuid"
        val result = Fuel
            .get(url)
            .authentication().bearer(accessToken())
            .responseObject<EsResponse>().third

        return when (result) {
            is Result.Success -> result.value.toStilling()
            is Result.Failure -> {
                log.error("Forsøkte å hente stilling fra URL $url", result.error.exception)
                null
            }
        }
    }

    private data class EsResponse(
        val _source: EsSource
    ) {
        fun toStilling(): Stilling = Stilling(
            stillingtittel = _source.stilling.styrkEllerTittel,
            søknadsfrist = _source.stilling.properties.applicationdue,
            arbeidsgiver = _source.stilling.employer.name,
            arbeidssteder = _source.stilling.locations.map(EsArbeidssted::toArbeidssted),
            kontaktinfo = _source.stilling.contacts.map(EsContact::toKontakt),
            stillingskategori = _source.stillingsinfo?.stillingskategori,
        )

        private data class EsSource(
            val stilling: EsStilling,
            val stillingsinfo: EsStillingsinfo?
        )

        private data class EsStilling(
            val styrkEllerTittel: String,
            val properties: Properties,
            val employer: Employer,
            val locations: List<EsArbeidssted>,
            val contacts: List<EsContact>
        )

        private data class EsStillingsinfo(
            val eierNavident: String?,
            val eierNavn: String?,
            val notat: String?,
            val stillingsid: String,
            val stillingsinfoid: String,
            val stillingskategori: String?,
        )

        private data class EsContact(
            val name: String,
            val title: String,
            val email: String,
            val phone: String,
            val role: String
        ) {
            fun toKontakt() = Kontakt(
                navn = name,
                tittel = title,
                epost = email,
                mobil = phone,
                rolle = role
            )
        }

        private data class Employer(
            val name: String
        )

        private data class Properties(
            val applicationdue: String?
        )

        private data class EsArbeidssted(
            val address: String?,
            val postalCode: String?,
            val city: String?,
            val county: String?,
            val municipal: String?,
            val country: String,
        ) {
            fun toArbeidssted() = Arbeidssted(
                adresse = address,
                postkode = postalCode,
                by = city,
                kommune = municipal,
                fylke = county,
                land = country
            )
        }
    }
}


