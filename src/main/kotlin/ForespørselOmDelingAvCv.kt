import java.sql.ResultSet
import java.time.LocalDateTime

data class ForespørselOmDelingAvCv(
    val aktørId: String,
    val stillingsId: String,

    val deltStatus: DeltStatus,
    val deltTidspunkt: LocalDateTime,
    val deltAv: String,

    val svar: Svar,
    val svarTidspunkt: LocalDateTime?,

    val sendtTilKafkaTidspunkt: LocalDateTime?
) {
    companion object {
        fun fromDb(rs: ResultSet) = ForespørselOmDelingAvCv(
            aktørId = rs.getString("aktor_id"),
            stillingsId = rs.getString("stilling_id"),
            deltStatus = DeltStatus.valueOf(rs.getString("delt_status")),
            deltTidspunkt = rs.getTimestamp("delt_tidspunkt").toLocalDateTime(),
            deltAv = rs.getString("delt_av"),
            svar = Svar.valueOf(rs.getString("svar")),
            svarTidspunkt = rs.getTimestamp("svar_tidspunkt")?.toLocalDateTime(),
            sendtTilKafkaTidspunkt = rs.getTimestamp("sendt_til_kafka_tidspunkt")?.toLocalDateTime()
        )
    }
}

enum class DeltStatus {
    SENDT,
    IKKE_SENDT,
}

enum class Svar {
    IKKE_SVART,
    JA,
    NEI,
}
