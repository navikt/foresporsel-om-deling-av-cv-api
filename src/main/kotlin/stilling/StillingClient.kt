package stilling

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import utils.Cluster
import utils.log
import java.util.*

class StillingClient(private val accessToken: () -> String) {
    private val stillingssokProxyDokumentUrl = when (Cluster.current) {
        Cluster.DEV_FSS -> "https://rekrutteringsbistand-stillingssok-proxy.dev.intern.nav.no/stilling/_doc"
        Cluster.PROD_FSS -> "https://rekrutteringsbistand-stillingssok-proxy.intern.nav.no/stilling/_doc"
    }

    fun hentStilling(uuid: UUID): Stilling? {
        val result = Fuel
            .get("$stillingssokProxyDokumentUrl/$uuid")
            .authentication().bearer(accessToken())
            .responseObject<EsResponse>().third

        return when (result) {
            is Result.Success -> result.value.toStilling()
            is Result.Failure -> {
                log.error("Fant ikke en stilling med id $uuid:", result.error.exception)

                return null
            }
        }.also { log.info("Hentet stilling $it") }
    }
}

private data class EsResponse(
    val _source: EsSource
) {
    fun toStilling() = Stilling(
        stillingtittel = _source.stilling.title,
        søknadsfrist = _source.stilling.properties.applicationdue,
        arbeidsgiver = _source.stilling.employer.name,
        arbeidssteder = _source.stilling.locations.map(EsArbeidssted::toArbeidssted)
    )

    private data class EsSource(
        val stilling: EsStilling
    )

    private data class EsStilling(
        val title: String,
        val properties: Properties,
        val employer: Employer,
        val locations: List<EsArbeidssted>
    )

    private data class Employer(
        val name: String
    )

    private data class Properties(
        val applicationdue: String
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
