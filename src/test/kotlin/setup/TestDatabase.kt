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
            jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
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
                statement.setObject(3, it.forespørselId)
                statement.setString(4, it.deltStatus.toString())
                statement.setTimestamp(5, Timestamp.valueOf(it.deltTidspunkt))
                statement.setString(6, it.deltAv)
                statement.setTimestamp(7, Timestamp.valueOf(it.svarfrist))
                statement.setString(8, it.svar.toString())
                statement.setTimestamp(9, null)
                statement.setTimestamp(10, null)
                statement.setString(11, it.callId)

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
