import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import utils.Cluster
import java.util.*

class StillingKlient {

    private val stillingssokProxyDokumentUrl = when (Cluster.current) {
        Cluster.DEV_FSS -> "https://rekrutteringsbistand-stillingssok-proxy.dev.intern.nav.no/stilling/_doc"
        Cluster.PROD_FSS -> "https://rekrutteringsbistand-stillingssok-proxy.intern.nav.no/stilling/_doc"
    }

    fun hentStilling(uuid: UUID): Stilling {
        val (_, _, result) = Fuel
            .get("$stillingssokProxyDokumentUrl/$uuid")
            .responseObject<EsResponse>()

        when (result) {
            is Result.Success -> return result.value.toStilling()
            is Result.Failure -> throw RuntimeException("Kunne ikke hente stilling med id $uuid", result.error)
        }
    }
}

class Stilling(
    val stillingtittel: String,
    val soknadsfrist: String,
    val arbeidsgiver: String,
    val arbeidssteder: List<Arbeidssted>
)

class Arbeidssted(
    val adresse: String?,
    val postkode: String?,
    val by: String?,
    val kommune: String?,
    val fylke: String?,
    val land: String
)

private data class EsResponse(
    val _source: EsSource
) {
    fun toStilling() = Stilling(
        stillingtittel = _source.stilling.title,
        soknadsfrist = _source.stilling.properties.applicationdue,
        arbeidsgiver = _source.stilling.employer.name,
        arbeidssteder = _source.stilling.locations.map {
            Arbeidssted(
                it.address,
                it.postalCode,
                it.
            )
        }
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
        val county: String?,
        val municipal: String?,
        val country: String,
    )
}

