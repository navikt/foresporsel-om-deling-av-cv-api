import utils.log
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
        svarfrist: LocalDateTime,
        deltAvNavIdent: String,
        callId: String
    ) {
        val lagreBatchSql = """
                INSERT INTO foresporsel_om_deling_av_cv (
                    aktor_id, stilling_id, foresporsel_id, delt_status, delt_tidspunkt, delt_av, svarfrist, call_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(lagreBatchSql)

            aktørIder.forEach { aktørId ->
                statement.setString(1, aktørId)
                statement.setObject(2, stillingsId)
                statement.setObject(3, UUID.randomUUID())
                statement.setString(4, DeltStatus.IKKE_SENDT.toString())
                statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()))
                statement.setString(6, deltAvNavIdent)
                statement.setTimestamp(7, Timestamp.valueOf(svarfrist))
                statement.setString(8, callId)
                statement.addBatch()
            }

            statement.executeBatch()
        }
    }

    fun hentUsendteForespørsler(): List<Forespørsel> {
        val hentUsendteSql = """
            SELECT * from foresporsel_om_deling_av_cv WHERE delt_status = '${DeltStatus.IKKE_SENDT}'
        """.trimIndent()

        dataSource.connection.use { connection ->
            return connection.prepareStatement(hentUsendteSql).executeQuery().tilForespørsler()
        }
    }

    fun hentForespørsler(fraOgMed: LocalDateTime, tilOgMed: LocalDateTime): List<Forespørsel> {
        val sql = """
            SELECT * FROM foresporsel_om_deling_av_cv 
            WHERE delt_tidspunkt BETWEEN ? AND ?
        """.trimIndent()

        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(sql)

            statement.setTimestamp(1, Timestamp.valueOf(fraOgMed))
            statement.setTimestamp(2, Timestamp.valueOf(tilOgMed))
            return statement.executeQuery().tilForespørsler()
        }
    }

    fun markerForespørselSendt(id: Long) {
        val oppdaterDeltStatusSql = """
            UPDATE foresporsel_om_deling_av_cv
                SET delt_status = ?, sendt_til_kafka_tidspunkt = ?
                WHERE id = ?
        """.trimIndent()

        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(oppdaterDeltStatusSql)

            statement.setString(1, DeltStatus.SENDT.toString())
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()))
            statement.setLong(3, id)

            statement.executeUpdate()
        }
    }

    fun oppdaterMedRespons(forespørselId: UUID, tilstand: Tilstand, svar: Svar?, begrunnelseForAtAktivitetIkkeBleOpprettet: BegrunnelseForAtAktivitetIkkeBleOpprettet?) {
        val oppdaterSvarSql = """
            UPDATE foresporsel_om_deling_av_cv
                SET tilstand = ?, svar = ?, svar_tidspunkt = ?, svart_av_ident = ?, svart_av_ident_type = ?, begrunnelse_for_at_aktivitet_ikke_ble_opprettet = ?
                WHERE foresporsel_id = ?
        """.trimIndent()

        dataSource.connection.use { connection ->

            val antallOppdaterteRader = connection.prepareStatement(oppdaterSvarSql).apply {
                setString(1, tilstand.toString())

                if (svar != null) {
                    setBoolean(2, svar.harSvartJa)
                    setTimestamp(3, Timestamp.valueOf(svar.svarTidspunkt))
                    setString(4, svar.svartAv.ident)
                    setString(5, svar.svartAv.identType.toString())
                } else {
                    setObject(2, null)
                    setTimestamp(3, null)
                    setString(4, null)
                    setString(5, null)
                }

                setObject(6, begrunnelseForAtAktivitetIkkeBleOpprettet?.name)

                setObject(7, forespørselId)

            }.executeUpdate()

            if (antallOppdaterteRader != 1) {
                log.error("Oppdaterte et markelig antall rader ($antallOppdaterteRader) for svar: $svar")
            }
        }
    }

    fun hentForespørsler(stillingsId: UUID): List<Forespørsel> {
        val hentForespørslerSql = """
            SELECT * from foresporsel_om_deling_av_cv WHERE stilling_id = ?
        """.trimIndent()

        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(hentForespørslerSql)

            statement.setObject(1, stillingsId)
            return statement.executeQuery().tilForespørsler()
        }
    }

    fun hentForespørslerForKandidat(aktørId: String): List<Forespørsel> {
        val hentForespørslerForKandidatSql = """
            SELECT * from foresporsel_om_deling_av_cv WHERE aktor_id = ?
        """.trimIndent()

        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(hentForespørslerForKandidatSql)

            statement.setString(1, aktørId)
            return statement.executeQuery().tilForespørsler()
        }
    }

    fun hentSisteForespørselForKandidatOgStilling(aktørId: String, stillingsId: UUID): Forespørsel? {
        val hentSisteForespørsel = """
            SELECT * from foresporsel_om_deling_av_cv
             WHERE aktor_id = ? AND stilling_id = ?
             ORDER BY id DESC
             LIMIT 1
        """.trimIndent()

        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(hentSisteForespørsel)

            statement.setString(1, aktørId)
            statement.setObject(2, stillingsId)
            return statement.executeQuery().tilForespørsel()
        }
    }

    fun insertParameters(count: Int): String =
        Array(count) { "?" }.joinToString(",")

    fun minstEnKandidatHarFåttForespørsel(stillingsId: UUID, aktorIder: List<String>): Boolean {
        val forespørselFinnesSql = """
            SELECT * FROM foresporsel_om_deling_av_cv
                WHERE stilling_id = ?
                AND aktor_id IN (${insertParameters(aktorIder.size)})
        """.trimIndent()

        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(forespørselFinnesSql)
            var parameterIndex = 1
            statement.setObject(parameterIndex++, stillingsId)

            aktorIder.forEach {
                statement.setString(parameterIndex++, it)
            }

            return statement.executeQuery().tilForespørsler().isNotEmpty()
        }
    }

    private fun ResultSet.tilForespørsler() = generateSequence {
        if (next()) Forespørsel.fromDb(this)
        else null
    }.toList()

    private fun ResultSet.tilForespørsel() =
        if (next()) Forespørsel.fromDb(this)
        else null
}
