package stilling

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import utils.Cluster
import utils.log
import java.util.*

class StillingKlient(private val accessToken: () -> String) {
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
            is Result.Success -> result.value.toStilling().also { log.info("Hentet stilling $it") }
            is Result.Failure -> {
                log.error("Fant ikke en stilling med id $uuid:", result.error.exception)
                null
            }
        }
    }
}

private data class EsResponse(
    private val _source: EsSource
) {
    fun toStilling() = Stilling(
        stillingtittel = _source.stilling.title,
        søknadsfrist = _source.stilling.properties.applicationdue,
        arbeidsgiver = _source.stilling.employer.name,
        arbeidssteder = _source.stilling.locations.map(EsArbeidssted::toArbeidssted),
        contacts = _source.stilling.contacts
    )

    data class EsSource(
        val stilling: EsStilling
    )

    data class EsStilling(
        val title: String,
        val properties: Properties,
        val employer: Employer,
        val locations: List<EsArbeidssted>,
        val contacts: List<EsContact>
    )

    data class EsContact(
        val name: String,
        val title: String,
        val email: String,
        val phone: String,
        val role: String
    ){
        fun toContact = Contact(
            name = this.name,
            title = this.title,
            email = this.email,
            phone = this.phone,
            role = this.role
        )
    }

    data class Employer(
        val name: String
    )

    data class Properties(
        val applicationdue: String
    )

    data class EsArbeidssted(
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
