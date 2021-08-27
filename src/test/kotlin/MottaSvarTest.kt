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
import kotlin.test.assertNull
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

            val svartAv = Ident(forespørsel.aktørId, IdentTypeEnum.AKTOR_ID)
            val svarTidspunkt = LocalDateTime.now()
            val svarKafkamelding = DelingAvCvRespons(
                forespørsel.forespørselId.toString(),
                forespørsel.aktørId,
                aktivitetId.toString(),
                TilstandEnum.HAR_SVART,
                no.nav.veilarbaktivitet.avro.Svar(
                    svarTidspunkt.toInstant(ZoneOffset.UTC),
                    svartAv,
                    true
                )
            )

            mottaSvarKafkamelding(mockConsumer, svarKafkamelding)

            assertTrueInnen(2) {
                val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.aktørId }
                val svarIOppdatertForespørsel = lagredeForespørsler[forespørsel.aktørId]

                svarIOppdatertForespørsel?.tilstand == Tilstand.HAR_SVART &&
                        svarIOppdatertForespørsel.svar?.svar == true &&
                        svarIOppdatertForespørsel.svar?.svarTidspunkt == svarTidspunkt &&
                        svarIOppdatertForespørsel.svar?.svartAv?.ident == svartAv.getIdent()
            }

            val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.aktørId }
            assertNull(lagredeForespørsler[upåvirketForespørsel.aktørId]?.svar)
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

            val svarKafkamelding = DelingAvCvRespons(
                enForespørsel.forespørselId.toString(),
                enForespørsel.aktørId,
                aktivitetId.toString(),
                TilstandEnum.HAR_SVART,
                no.nav.veilarbaktivitet.avro.Svar(
                    LocalDateTime.now().toInstant(ZoneOffset.UTC),
                    Ident(enForespørsel.aktørId, IdentTypeEnum.AKTOR_ID),
                    true
                )
            )

            mottaSvarKafkamelding(mockConsumer, svarKafkamelding)

            assertTrueInnen(2) {
                val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.deltAv }
                val svarIOppdatertForespørsel = lagredeForespørsler[enVeileder]?.svar?.svar

                svarIOppdatertForespørsel == true
            }

            val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.deltAv }
            assertNull(lagredeForespørsler[enAnnenVeileder]?.svar?.svar)
        }
    }
}

private fun assertTrueInnen(timeoutSekunder: Int, conditional: (Any) -> Boolean) =
    assertTrue((0..(timeoutSekunder*10)).any(sleepIfFalse(conditional)))

private fun sleepIfFalse(conditional: (Any) -> Boolean): (Any) -> Boolean =
    { conditional(it).also { answer -> if(!answer) Thread.sleep(100) } }
