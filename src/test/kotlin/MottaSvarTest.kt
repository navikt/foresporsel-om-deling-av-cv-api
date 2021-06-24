import mottasvar.Svar
import no.nav.rekrutteringsbistand.avro.SvarPaForesporselOmDelingAvCv
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import setup.TestDatabase
import setup.mockConsumer
import setup.mottaSvarKafkamelding
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MottaSvarTest {

    @Test
    fun `Mottatt svar skal oppdatere riktig forespørsel i databasen`() {
        val database = TestDatabase()
        val mockConsumer = mockConsumer()

        startLokalApp(database, consumer = mockConsumer).use {
            val forespørsel = enForespørsel("123", DeltStatus.SENDT)
            val upåvirketForespørsel = enForespørsel("234", DeltStatus.SENDT)

            database.lagreBatch(listOf(forespørsel, upåvirketForespørsel))

            val svarKafkamelding = SvarPaForesporselOmDelingAvCv(
                forespørsel.forespørselId.toString(), Svar.JA.toString(), null
            )

            mottaSvarKafkamelding(mockConsumer, svarKafkamelding)

            assertTrueInnen(2) {
                val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.aktørId }
                val svarIOppdatertForespørsel = lagredeForespørsler[forespørsel.aktørId]?.svar

                svarIOppdatertForespørsel == Svar.JA
            }
            val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.aktørId }
            assertEquals(Svar.IKKE_SVART, lagredeForespørsler[upåvirketForespørsel.aktørId]?.svar)
        }
    }


    @Test
    fun `Mottatt svar skal ikke oppdatere andre forespørsler i databasen`() {
        val database = TestDatabase()
        val mockConsumer = mockConsumer()

        startLokalApp(database, consumer = mockConsumer).use {
            val aktørId = "123"
            val stillingsId = UUID.randomUUID()
            val enVeileder = "Eldste veileder"
            val enAnnenVeileder = "Nyeste veileder"

            val enForespørsel = enForespørsel(aktørId, DeltStatus.SENDT, stillingsId = stillingsId,  deltAv = enVeileder)
            val enAnnenForespørsel = enForespørsel(aktørId, DeltStatus.SENDT, stillingsId = stillingsId, deltAv = enAnnenVeileder)

            database.lagreBatch(listOf(enForespørsel, enAnnenForespørsel))

            val svarKafkamelding = SvarPaForesporselOmDelingAvCv(
                enForespørsel.forespørselId.toString(), Svar.JA.toString(), null
            )

            mottaSvarKafkamelding(mockConsumer, svarKafkamelding)

            assertTrueInnen(2) {
                val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.deltAv }
                val svarIOppdatertForespørsel = lagredeForespørsler[enVeileder]?.svar

                svarIOppdatertForespørsel == Svar.JA
            }

            val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.deltAv }
            assertEquals(Svar.IKKE_SVART, lagredeForespørsler[enAnnenVeileder]?.svar)
        }
    }
}

private fun assertTrueInnen(timeoutSekunder: Int, conditional: (Any) -> Boolean) =
    assertTrue((0..(timeoutSekunder*10)).any(sleepIfFalse(conditional)))

private fun sleepIfFalse(conditional: (Any) -> Boolean): (Any) -> Boolean =
    { conditional(it).also { answer -> if(!answer) Thread.sleep(100) } }
