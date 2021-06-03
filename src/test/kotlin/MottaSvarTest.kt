import mottasvar.Svar
import no.nav.rekrutteringsbistand.avro.SvarPaForesporselOmDelingAvCv
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import setup.TestDatabase
import setup.mockConsumer
import setup.mottaSvarKafkamelding
import java.time.LocalDateTime
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
                forespørsel.aktørId, forespørsel.stillingsId.toString(), Svar.JA.toString(), null
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
    fun `Mottatt svar skal bare oppdatere den nyeste forespørselen om en gitt stillingsid og aktørid i databasen`() {
        val database = TestDatabase()
        val mockConsumer = mockConsumer()

        startLokalApp(database, consumer = mockConsumer).use {
            val aktørId = "123"
            val stillingsId = UUID.randomUUID()
            val eldsteVeileder = "Eldste veileder"
            val nyesteVeileder = "Nyeste veileder"
            val eldsteForespørsel = enForespørsel(aktørId, DeltStatus.SENDT, stillingsId = stillingsId,  deltAv = eldsteVeileder)
            val nyesteForespørsel = enForespørsel(aktørId, DeltStatus.SENDT, stillingsId = stillingsId, deltAv = nyesteVeileder)

            database.lagreBatch(listOf(eldsteForespørsel))
            database.lagreBatch(listOf(nyesteForespørsel))

            val svarKafkamelding = SvarPaForesporselOmDelingAvCv(
                eldsteForespørsel.aktørId, eldsteForespørsel.stillingsId.toString(), Svar.JA.toString(), null
            )

            mottaSvarKafkamelding(mockConsumer, svarKafkamelding)

            assertTrueInnen(2) {
                val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.deltAv }
                val svarIOppdatertForespørsel = lagredeForespørsler[nyesteVeileder]?.svar

                svarIOppdatertForespørsel == Svar.JA
            }

            val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.deltAv }
            assertEquals(Svar.IKKE_SVART, lagredeForespørsler[eldsteVeileder]?.svar)
        }
    }

    private fun enForespørsel(aktørId: String, deltStatus: DeltStatus, deltTidspunkt: LocalDateTime = LocalDateTime.now(), stillingsId: UUID = UUID.randomUUID(), deltAv: String = "veileder") =
        Forespørsel(
            id = 0,
            aktørId = aktørId,
            stillingsId = stillingsId,
            deltStatus = deltStatus,
            deltTidspunkt = deltTidspunkt,
            deltAv = deltAv,
            svar = Svar.IKKE_SVART,
            svarTidspunkt = null,
            sendtTilKafkaTidspunkt = null,
            callId = UUID.randomUUID()
        )
}

private fun assertTrueInnen(timeoutSekunder: Int, conditional: (Any) -> Boolean) =
    assertTrue((0..(timeoutSekunder*10)).any(sleepIfFalse(conditional)))

private fun sleepIfFalse(conditional: (Any) -> Boolean): (Any) -> Boolean =
    { conditional(it).also { answer -> if(!answer) Thread.sleep(100) } }