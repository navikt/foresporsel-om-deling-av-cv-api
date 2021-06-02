package mottasvar

import java.util.*

data class SvarPåForespørsel(
    val aktørId:String,
    val stillingId: UUID,
    val svar: Svar
)

enum class Svar {
    IKKE_SVART,
    JA,
    NEI,
}
