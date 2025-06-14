import no.nav.veilarbaktivitet.avro.*
import no.nav.veilarbaktivitet.avro.Ident
import no.nav.veilarbaktivitet.avro.Svar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import setup.TestDatabase
import setup.mockConsumer
import setup.mottaSvarKafkamelding
import utils.atOslo
import java.time.LocalDateTime
import java.util.*
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
                Svar(
                    svarTidspunkt.atOslo().toInstant(),
                    svartAv,
                    true
                ),
                nullKanIkkeOppretteBegrunnelse
            )

            mottaSvarKafkamelding(mockConsumer, svarKafkamelding)

            assertTrueWithTimeout {
                val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.aktørId }
                val svarIOppdatertForespørsel = lagredeForespørsler[forespørsel.aktørId]?.svar

                svarIOppdatertForespørsel != null &&
                        svarIOppdatertForespørsel.harSvartJa &&
                        svarIOppdatertForespørsel.svartAv.ident == svartAv.getIdent()
            }

            val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.aktørId }
            assertNull(lagredeForespørsler[upåvirketForespørsel.aktørId]?.svar)
        }
    }

    @Test
    fun `Kan motta svar med svart av veileder og svarTidspunkt langt tilbake i tid`() {
        val database = TestDatabase()
        val mockConsumer = mockConsumer()

        startLokalApp(database, consumer = mockConsumer).use {
            val forespørsel = enForespørsel("123", DeltStatus.SENDT)
            val aktivitetId = UUID.randomUUID()
            database.lagreBatch(listOf(forespørsel))

            val svartAv = Ident("Z123456", IdentTypeEnum.NAV_IDENT)
            val svarTidspunkt = LocalDateTime.now().minusMonths(2);

            val svarKafkamelding = DelingAvCvRespons(
                forespørsel.forespørselId.toString(),
                forespørsel.aktørId,
                aktivitetId.toString(),
                TilstandEnum.HAR_SVART,
                Svar(
                    svarTidspunkt.atOslo().toInstant(),
                    svartAv,
                    true
                ),
                nullKanIkkeOppretteBegrunnelse
            )

            mottaSvarKafkamelding(mockConsumer, svarKafkamelding)

            val lagredeForespørsler = database.hentAlleForespørsler()
            assertThat(lagredeForespørsler.size).isOne
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

            val enForespørsel = enForespørsel(aktørId, DeltStatus.SENDT, stillingsId = stillingsId, deltAv = enVeileder)
            val enAnnenForespørsel =
                enForespørsel(aktørId, DeltStatus.SENDT, stillingsId = stillingsId, deltAv = enAnnenVeileder)

            database.lagreBatch(listOf(enForespørsel, enAnnenForespørsel))

            val svarKafkamelding = DelingAvCvRespons(
                enForespørsel.forespørselId.toString(),
                enForespørsel.aktørId,
                aktivitetId.toString(),
                TilstandEnum.HAR_SVART,
                Svar(
                    LocalDateTime.now().atOslo().toInstant(),
                    Ident(enForespørsel.aktørId, IdentTypeEnum.AKTOR_ID),
                    true
                ),
                nullKanIkkeOppretteBegrunnelse
            )

            mottaSvarKafkamelding(mockConsumer, svarKafkamelding)

            assertTrueWithTimeout {
                val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.deltAv }
                val svarIOppdatertForespørsel = lagredeForespørsler[enVeileder]?.svar?.harSvartJa

                svarIOppdatertForespørsel == true
            }

            val lagredeForespørsler = database.hentAlleForespørsler().associateBy { it.deltAv }
            assertNull(lagredeForespørsler[enAnnenVeileder]?.svar?.harSvartJa)
        }
    }

    @Test
    fun `Motta response skal håndtere nullable svar`() {
        val nullSvar: Svar? = null
        val database = TestDatabase()
        val mockConsumer = mockConsumer()

        startLokalApp(database, consumer = mockConsumer).use {
            val forespørsel = enForespørsel("123", DeltStatus.SENDT)
            database.lagreBatch(listOf(forespørsel))

            val svarKafkamelding = DelingAvCvRespons(
                forespørsel.forespørselId.toString(),
                forespørsel.aktørId,
                UUID.randomUUID().toString(),
                TilstandEnum.PROVER_VARSLING,
                nullSvar,
                nullKanIkkeOppretteBegrunnelse
            )

            mottaSvarKafkamelding(mockConsumer, svarKafkamelding)

            assertTrueWithTimeout {
                val lagretForespørsel = database.hentAlleForespørsler().first()

                lagretForespørsel.tilstand == Tilstand.PROVER_VARSLING && lagretForespørsel.svar == null
            }
        }
    }

    @Test
    fun `Motta response skal lagre begrunnelse i database hvis aktivitetskortet ikke ble opprettet`() {
        val nullSvar: Svar? = null
        val database = TestDatabase()
        val mockConsumer = mockConsumer()

        startLokalApp(database, consumer = mockConsumer).use {
            val forespørsel = enForespørsel("123", DeltStatus.SENDT)
            database.lagreBatch(listOf(forespørsel))

            val svarKafkamelding = DelingAvCvRespons(
                forespørsel.forespørselId.toString(),
                forespørsel.aktørId,
                UUID.randomUUID().toString(),
                TilstandEnum.KAN_IKKE_OPPRETTE,
                nullSvar,
                KanIkkeOppretteBegrunnelse(null, BegrunnelseEnum.UGYLDIG_OPPFOLGINGSSTATUS)
            )

            mottaSvarKafkamelding(mockConsumer, svarKafkamelding)

            assertTrueWithTimeout {
                val lagretForespørsel = database.hentAlleForespørsler().first()

                lagretForespørsel.tilstand == Tilstand.KAN_IKKE_OPPRETTE &&
                        lagretForespørsel.svar == null &&
                        lagretForespørsel.begrunnelseForAtAktivitetIkkeBleOpprettet == BegrunnelseForAtAktivitetIkkeBleOpprettet.UGYLDIG_OPPFOLGINGSSTATUS
            }
        }
    }
}

private fun assertTrueWithTimeout(timeoutSeconds: Int = 2, conditional: (Any) -> Boolean) =
    assertTrue((0..(timeoutSeconds * 10)).any(sleepIfFalse(conditional)))

private fun sleepIfFalse(conditional: (Any) -> Boolean): (Any) -> Boolean =
    { conditional(it).also { answer -> if (!answer) Thread.sleep(100) } }

private val nullKanIkkeOppretteBegrunnelse: KanIkkeOppretteBegrunnelse? = null
