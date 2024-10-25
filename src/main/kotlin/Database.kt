import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import org.flywaydb.core.Flyway
import utils.Miljø
import utils.Miljø.*
import javax.sql.DataSource

private const val databasenavn = "foresporsel-om-deling-av-cv-pg15"

class Database {
    val dataSource: DataSource

    private val config: DbConf = when (Miljø.current) {
        DEV_FSS -> DbConf(
            mountPath = "postgresql/preprod-fss",
            jdbcUrl = "jdbc:postgresql://b27dbvl035.preprod.local:5432/$databasenavn"
        )
        PROD_FSS -> DbConf(
            mountPath = "postgresql/prod-fss",
            jdbcUrl = "jdbc:postgresql://A01DBVL037.adeo.no:5432/$databasenavn"
        )
        LOKAL -> throw TODO()
    }

    init {
        dataSource = opprettDataSource(role = "user")
        kjørFlywayMigreringer()
    }

    private fun opprettDataSource(role: String): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            minimumIdle = 1
            maximumPoolSize = 2
            driverClassName = "org.postgresql.Driver"
        }

        return HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
            hikariConfig,
            config.mountPath,
            "$databasenavn-$role"
        )
    }

    private fun kjørFlywayMigreringer() {
        Flyway.configure()
            .dataSource(opprettDataSource(role = "admin"))
            .initSql("SET ROLE \"$databasenavn-admin\"")
            .load()
            .migrate()
    }

    data class DbConf(val mountPath: String, val jdbcUrl: String)
}
