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
                statement.setString(2, stillingsId.toString())
                statement.setString(3, DeltStatus.IKKE_SENDT.toString())
                statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()))
                statement.setString(5, deltAvNavIdent)
                statement.setString(6, Svar.IKKE_SVART.toString())
                statement.setTimestamp(7, null)
                statement.setTimestamp(8, null)
                statement.setString(9, callId.toString())
                statement.addBatch()
            }

            statement.executeBatch()
        }
    }

    fun hentUsendteForespørsler(): List<ForespørselOmDelingAvCv> {
        dataSource.connection.use { connection ->
            val resultSet = connection.prepareStatement(HENT_USENDTE_SQL).executeQuery()

            return generateSequence {
                if (resultSet.next()) ForespørselOmDelingAvCv.fromDb(resultSet)
                else null
            }.toList()
        }
    }

    fun markerForespørselSendt(id: Long) {
        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(OPPDATER_SQL)

            statement.setString(1, DeltStatus.SENDT.toString())
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()))
            statement.setLong(3, id)

            statement.executeUpdate()
        }
    }

    companion object {
        val LAGRE_BATCH_SQL = """
            INSERT INTO foresporsel_om_deling_av_cv (
                aktor_id, stilling_id, delt_status, delt_tidspunkt, delt_av, svar, svar_tidspunkt, sendt_til_kafka_tidspunkt, call_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val OPPDATER_SQL = """
            UPDATE foresporsel_om_deling_av_cv SET delt_status = ?, sendt_til_kafka_tidspunkt = ? WHERE id = ?
        """.trimIndent()

        val HENT_USENDTE_SQL = """
            SELECT * from foresporsel_om_deling_av_cv WHERE delt_status = '${DeltStatus.IKKE_SENDT}'
        """.trimIndent()
    }
}
