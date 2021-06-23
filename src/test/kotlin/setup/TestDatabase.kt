package setup

import Forespørsel
import Repository.Companion.LAGRE_BATCH_SQL
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Timestamp
import javax.sql.DataSource

class TestDatabase {

    val dataSource: DataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
            validate()
        })

    init {
        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
        slettAlt()
    }

    fun lagreBatch(forespørselOmDelingAvCver: List<Forespørsel>) {
        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(LAGRE_BATCH_SQL.trimIndent())

            forespørselOmDelingAvCver.forEach {
                statement.setString(1, it.aktørId)
                statement.setObject(2, it.stillingsId)
                statement.setString(3, it.deltStatus.toString())
                statement.setTimestamp(4, Timestamp.valueOf(it.deltTidspunkt))
                statement.setString(5, it.deltAv)
                statement.setTimestamp(6, Timestamp.valueOf(it.svarfrist))
                statement.setString(7, it.svar.toString())
                statement.setTimestamp(8, null)
                statement.setTimestamp(9, null)
                statement.setObject(10, it.callId)

                statement.addBatch()
            }

            statement.executeBatch()
        }
    }

    fun hentAlleForespørsler(): List<Forespørsel> {
        dataSource.connection.use { connection ->
            val resultSet = connection.prepareStatement(
                "SELECT * from foresporsel_om_deling_av_cv"
            ).executeQuery()

            return generateSequence {
                if (resultSet.next()) Forespørsel.fromDb(resultSet)
                else null
            }.toList()
        }
    }

    fun slettAlt() {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "DELETE FROM foresporsel_om_deling_av_cv"
            ).execute()
        }
    }
}
