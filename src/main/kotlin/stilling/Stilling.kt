package stilling

data class Stilling(
    val stillingtittel: String,
    val søknadsfrist: String,
    val arbeidsgiver: String,
    val arbeidssteder: List<Arbeidssted>,
    val kontaktinfo: List<Kontakt>?
)

data class Kontakt(
    val navn: String,
    val tittel: String,
    val epost: String,
    val mobil: String,
    val rolle: String
)

data class Arbeidssted(
    val adresse: String?,
    val postkode: String?,
    val by: String?,
    val kommune: String?,
    val fylke: String?,
    val land: String
)
