package mottasvar

import java.util.*

data class SvarPåForespørsel(
    val forespørselId: UUID,
    val svar: Svar,
    val brukerVarslet: Boolean,
    val aktivitetOpprettet: Boolean
)

enum class Svar {
    IKKE_SVART,
    JA,
    NEI,
}
