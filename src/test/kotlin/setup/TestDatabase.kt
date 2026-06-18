package setup

import Forespørsel
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Timestamp
import java.sql.Types
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
        val lagreBatchSql = """
                INSERT INTO foresporsel_om_deling_av_cv (
                    aktor_id, stilling_id, foresporsel_id, delt_status, delt_tidspunkt, delt_av, svarfrist, tilstand, call_id, svar, svar_tidspunkt, svart_av_ident, svart_av_ident_type, nav_kontor
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(lagreBatchSql.trimIndent())

            forespørselOmDelingAvCver.forEach {
                statement.setString(1, it.aktørId)
                statement.setObject(2, it.stillingsId)
                statement.setObject(3, it.forespørselId)
                statement.setString(4, it.deltStatus.toString())
                statement.setTimestamp(5, Timestamp.valueOf(it.deltTidspunkt))
                statement.setString(6, it.deltAv)
                statement.setTimestamp(7, Timestamp.valueOf(it.svarfrist.toLocalDateTime()))
                statement.setString(8, it.tilstand.toString())
                statement.setString(9, it.callId)

                if (it.svar != null) {
                    statement.setBoolean(10, it.svar!!.harSvartJa)
                    statement.setTimestamp(11, Timestamp.valueOf(it.svar?.svarTidspunkt))
                } else {
                    statement.setNull(10, Types.BOOLEAN)
                    statement.setNull(11, Types.TIMESTAMP)
                }

                statement.setString(12, it.svar?.svartAv?.ident)
                statement.setString(13, it.svar?.svartAv?.identType.toString())
                statement.setString(14, it.navKontor)

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
