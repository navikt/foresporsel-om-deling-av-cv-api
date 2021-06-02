import mottasvar.Svar
import mottasvar.SvarPåForespørsel
import utils.log
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class Repository(private val dataSource: DataSource) {

    fun lagreUsendteForespørsler(aktørIder: List<String>, stillingsId: UUID, deltAvNavIdent: String, callId: UUID) {
        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(LAGRE_BATCH_SQL)

            aktørIder.forEach { aktørId ->
                statement.setString(1, aktørId)
                statement.setObject(2, stillingsId)
                statement.setString(3, DeltStatus.IKKE_SENDT.toString())
                statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()))
                statement.setString(5, deltAvNavIdent)
                statement.setString(6, Svar.IKKE_SVART.toString())
                statement.setTimestamp(7, null)
                statement.setTimestamp(8, null)
                statement.setObject(9, callId)
                statement.addBatch()
            }

            statement.executeBatch()
        }
    }

    fun hentUsendteForespørsler(): List<Forespørsel> {
        dataSource.connection.use { connection ->
            val resultSet = connection.prepareStatement(HENT_USENDTE_SQL).executeQuery()

            return generateSequence {
                if (resultSet.next()) Forespørsel.fromDb(resultSet)
                else null
            }.toList()
        }
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
            statement.setString(3, svar.aktørId)
            statement.setObject(4, svar.stillingId)

            val antallOppdaterteRader = statement.executeUpdate()
            if(antallOppdaterteRader != 1) {
                log.error("Oppdaterte et markelig antall rader ($antallOppdaterteRader) for svar: $svar")
            }
        }
    }

    companion object {
        val LAGRE_BATCH_SQL = """
            INSERT INTO foresporsel_om_deling_av_cv (
                aktor_id, stilling_id, delt_status, delt_tidspunkt, delt_av, svar, svar_tidspunkt, sendt_til_kafka_tidspunkt, call_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val OPPDATER_DELT_STATUS_SQL = """
            UPDATE foresporsel_om_deling_av_cv SET delt_status = ?, sendt_til_kafka_tidspunkt = ? WHERE id = ?
        """.trimIndent()

        val OPPDATER_SVAR_SQL = """
            UPDATE foresporsel_om_deling_av_cv SET svar = ?, svar_tidspunkt = ? WHERE id in 
              (SELECT max(id)
              FROM foresporsel_om_deling_av_cv 
              WHERE aktor_id = ? AND stilling_id = ? 
              GROUP BY aktor_id, stilling_id)
        """.trimIndent()

        val HENT_USENDTE_SQL = """
            SELECT * from foresporsel_om_deling_av_cv WHERE delt_status = '${DeltStatus.IKKE_SENDT}'
        """.trimIndent()
    }
}
