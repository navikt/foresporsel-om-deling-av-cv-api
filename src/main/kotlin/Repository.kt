import java.sql.Timestamp
import javax.sql.DataSource

class Repository(private val dataSource: DataSource) {

    fun lagreBatch(forespørselOmDelingAvCver: List<ForespørselOmDelingAvCv>) {
        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(LAGRE_BATCH_SQL)

            forespørselOmDelingAvCver.forEach {
                statement.setString(1, it.aktørId)
                statement.setString(2, it.stillingsId)
                statement.setString(3, it.deltStatus.toString())
                statement.setTimestamp(4, Timestamp.valueOf(it.deltTidspunkt))
                statement.setString(5, it.deltAv)
                statement.setString(6, it.svar.toString())
                statement.setTimestamp(7, null)

                statement.addBatch()
            }

            statement.executeUpdate()
        }
    }

    fun hentAlleForespørsler(): List<ForespørselOmDelingAvCv> {
        dataSource.connection.use { connection ->
            val resultSet = connection.prepareStatement(HENT_ALLE_SQL).executeQuery()

            return generateSequence {
                if (resultSet.next()) ForespørselOmDelingAvCv.fromDb(resultSet)
                else null
            }.toList()
        }
    }

    companion object {
        val LAGRE_BATCH_SQL = """
            INSERT INTO foresporsel_om_deling_av_cv (
                aktor_id, stilling_id, delt_status, delt_tidspunkt, delt_av, svar, svar_tidspunkt
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val HENT_ALLE_SQL = """
            SELECT * from foresporsel_om_deling_av_cv;
        """.trimIndent()
    }
}
