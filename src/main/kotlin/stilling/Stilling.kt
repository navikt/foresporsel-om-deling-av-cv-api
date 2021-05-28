package stilling

data class Stilling(
    val stillingtittel: String,
    val søknadsfrist: String,
    val arbeidsgiver: String,
    val arbeidssteder: List<Arbeidssted>
)

data class Arbeidssted(
    val adresse: String?,
    val postkode: String?,
    val by: String?,
    val kommune: String?,
    val fylke: String?,
    val land: String
)
