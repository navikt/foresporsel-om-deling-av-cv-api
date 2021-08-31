package mottasvar

import Svar
import Tilstand
import java.util.*

data class SvarPåForespørsel(
    val forespørselId: UUID,
    val tilstand: Tilstand,
    val svar: Svar?,
)
