import mottasvar.Svar
import mottasvar.SvarPåForespørsel
import utils.log
import java.sql.Array
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class Repository(private val dataSource: DataSource) {

    fun lagreUsendteForespørsler(
        aktørIder: List<String>,
        stillingsId: UUID,
        forespørselId: UUID,
        svarfrist: LocalDateTime,
        deltAvNavIdent: String,
        callId: String
    ) {
        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(LAGRE_BATCH_SQL)

            aktørIder.forEach { aktørId ->
                statement.setString(1, aktørId)
                statement.setObject(2, stillingsId)
                statement.setObject(3, forespørselId)
                statement.setString(4, DeltStatus.IKKE_SENDT.toString())
                statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()))
                statement.setString(6, deltAvNavIdent)
                statement.setTimestamp(7, Timestamp.valueOf(svarfrist))
                statement.setString(8, Svar.IKKE_SVART.toString())
                statement.setTimestamp(9, null)
                statement.setTimestamp(10, null)
                statement.setString(11, callId)
                statement.addBatch()
            }

            statement.executeBatch()
        }
    }

    fun hentUsendteForespørsler(): List<Forespørsel> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(HENT_USENDTE_SQL).executeQuery().tilForespørsler()
        }

    fun markerForespørselSendt(id: Long) {
        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(OPPDATER_DELT_STATUS_SQL)

            statement.setString(1, DeltStatus.SENDT.toString())
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()))
            statement.setLong(3, id)

            statement.executeUpdate()
        }
    }

    fun oppdaterMedSvar(svar: SvarPåForespørsel) {
        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(OPPDATER_SVAR_SQL)

            statement.setString(1, svar.svar.name)
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()))
            statement.setObject(3, svar.forespørselId)

            val antallOppdaterteRader = statement.executeUpdate()
            if (antallOppdaterteRader != 1) {
                log.error("Oppdaterte et markelig antall rader ($antallOppdaterteRader) for svar: $svar")
            }
        }
    }

    fun hentForespørsler(stillingsId: UUID) =
        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(HENT_FORESPØRSLER)

            statement.setObject(1, stillingsId)

            statement.executeQuery().tilForespørsler()
        }

    fun minstEnKandidatHarFåttForespørsel(stillingsId: UUID, aktorIder: List<String>): Boolean {
        val FORESPØRSEL_FINNES_SQL = """
            SELECT * FROM foresporsel_om_deling_av_cv
            WHERE stilling_id = ? AND aktor_id IN (?);
        """.trimIndent()

        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(FORESPØRSEL_FINNES_SQL)
            statement.setObject(1, stillingsId)

            val somArray = connection.createArrayOf("TEXT", aktorIder.toTypedArray())
            statement.setArray(2, somArray)

            return statement.executeQuery().tilForespørsler().isNotEmpty()
        }
    }

    private fun ResultSet.tilForespørsler() = generateSequence {
        if (next()) Forespørsel.fromDb(this)
        else null
    }.toList()

    companion object {
        val LAGRE_BATCH_SQL = """
            INSERT INTO foresporsel_om_deling_av_cv (
                aktor_id, stilling_id, foresporsel_id, delt_status, delt_tidspunkt, delt_av, svarfrist, svar, svar_tidspunkt, sendt_til_kafka_tidspunkt, call_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val OPPDATER_DELT_STATUS_SQL = """
            UPDATE foresporsel_om_deling_av_cv SET delt_status = ?, sendt_til_kafka_tidspunkt = ? WHERE id = ?
        """.trimIndent()

        val OPPDATER_SVAR_SQL = """
            UPDATE foresporsel_om_deling_av_cv SET svar = ?, svar_tidspunkt = ? WHERE foresporsel_id = ?
        """.trimIndent()

        val HENT_USENDTE_SQL = """
            SELECT * from foresporsel_om_deling_av_cv WHERE delt_status = '${DeltStatus.IKKE_SENDT}'
        """.trimIndent()

        val HENT_FORESPØRSLER = """
            SELECT * from foresporsel_om_deling_av_cv WHERE stilling_id = ?
        """.trimIndent()
    }
}
