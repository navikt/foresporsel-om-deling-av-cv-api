package stilling

data class Stilling(
    val stillingtittel: String,
    val søknadsfrist: String,
    val arbeidsgiver: String,
    val arbeidssteder: List<Arbeidssted>,
    val contacts: List<Contact>?
)

data class Contact(
    val name: String,
    val title: String,
    val email: String,
    val phone: String,
    val role: String
)

data class Arbeidssted(
    val adresse: String?,
    val postkode: String?,
    val by: String?,
    val kommune: String?,
    val fylke: String?,
    val land: String
)
