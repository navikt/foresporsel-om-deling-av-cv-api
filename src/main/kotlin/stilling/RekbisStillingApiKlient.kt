package stilling

import java.util.*


class RekbisStillingApiKlient {

    fun hent(stillingsId: UUID): RekbisStilling? { // TODO Are: Nullable returtype?
        return RekbisStilling("anyId", RekbisKontaktinfo(null, null, null, null)) // TODO Are
    }
}

data class RekbisKontaktinfo(
    val navn: String?,
    val tittel: String?,
    val tlfnr: String?,
    val epostadr: String?
)

data class RekbisStilling(
    val stillingsId: String,
    val kontaktinfo: RekbisKontaktinfo
) // TODO Are
