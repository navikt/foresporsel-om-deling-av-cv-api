package mottasvar

import java.util.*

data class SvarPåForespørsel(
    val forespørselId: UUID,
    val svar: Svar
)

enum class Svar {
    IKKE_SVART,
    JA,
    NEI,
}
