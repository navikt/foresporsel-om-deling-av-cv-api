package setup

import Forespørsel
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
            val statement = connection.prepareStatement(
                """
                    INSERT INTO foresporsel_om_deling_av_cv (
                        aktor_id, stilling_id, delt_status, delt_tidspunkt, delt_av, svar, svar_tidspunkt, sendt_til_kafka_tidspunkt, call_id
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                 """.trimIndent()
            )

            forespørselOmDelingAvCver.forEach {
                statement.setString(1, it.aktørId)
                statement.setObject(2, it.stillingsId)
                statement.setString(3, it.deltStatus.toString())
                statement.setTimestamp(4, Timestamp.valueOf(it.deltTidspunkt))
                statement.setString(5, it.deltAv)
                statement.setString(6, it.svar.toString())
                statement.setTimestamp(7, null)
                statement.setTimestamp(8, null)
                statement.setObject(9, it.callId)

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
