import mottasvar.Svar
import no.nav.veilarbaktivitet.avro.DelingAvCvRespons
import no.nav.veilarbaktivitet.avro.Ident
import no.nav.veilarbaktivitet.avro.IdentTypeEnum
import no.nav.veilarbaktivitet.avro.TilstandEnum
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import setup.TestDatabase
import setup.mockConsumer
import setup.mottaSvarKafkamelding
import java.time.LocalDateTime
import java.time.ZoneOffset
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
            val aktivitetId = UUID.randomUUID()

            database.lagreBatch(listOf(forespørsel, upåvirketForespørsel))

            // TODO
            val svarKafkamelding = DelingAvCvRespons(
                forespørsel.forespørselId.toString(),
                forespørsel.aktørId,
                aktivitetId.toString(),
                TilstandEnum.SVART,
                no.nav.veilarbaktivitet.avro.Svar(
                    LocalDateTime.now().toInstant(ZoneOffset.UTC),
                    Ident("hei", IdentTypeEnum.AKTOR_ID),
                    true
                )
            )

            mottaSvarKafkamelding(mockConsumer, svarKafkamelding)

            assertTrueInnen(2) {
                val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.aktørId }
                val svarIOppdatertForespørsel = lagredeForespørsler[forespørsel.aktørId]

                // TODO
                svarIOppdatertForespørsel?.svar == Svar.JA
//                svarIOppdatertForespørsel.brukerVarslet == svarKafkamelding.getBrukerVarslet() &&
//                svarIOppdatertForespørsel.aktivitetOpprettet == svarKafkamelding.getAktivitetOpprettet()
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
            val aktivitetId = UUID.randomUUID()

            val enForespørsel = enForespørsel(aktørId, DeltStatus.SENDT, stillingsId = stillingsId,  deltAv = enVeileder)
            val enAnnenForespørsel = enForespørsel(aktørId, DeltStatus.SENDT, stillingsId = stillingsId, deltAv = enAnnenVeileder)

            database.lagreBatch(listOf(enForespørsel, enAnnenForespørsel))

            // TODO
//            val svarKafkamelding = DelingAvCvRespons(
//                enForespørsel.forespørselId.toString(),
//                enForespørsel.aktørId,
//                aktivitetId.toString(),
//                true,
//                true,
//                SvarEnum.JA
//            )

//            mottaSvarKafkamelding(mockConsumer, svarKafkamelding)

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
