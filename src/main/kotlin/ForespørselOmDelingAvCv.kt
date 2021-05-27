import no.nav.rekrutteringsbistand.avro.Arbeidssted
import no.nav.rekrutteringsbistand.avro.ForesporselOmDelingAvCvKafkamelding
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

data class ForespørselOmDelingAvCv(
    val id: Long,
    val aktørId: String,
    val stillingsId: UUID,

    val deltStatus: DeltStatus,
    val deltTidspunkt: LocalDateTime,
    val deltAv: String,

    val svar: Svar,
    val svarTidspunkt: LocalDateTime?,

    val sendtTilKafkaTidspunkt: LocalDateTime?,
    val callId: UUID,
) {
    companion object {
        fun fromDb(rs: ResultSet) = ForespørselOmDelingAvCv(
            id = rs.getLong("id"),
            aktørId = rs.getString("aktor_id"),
            stillingsId = UUID.fromString(rs.getString("stilling_id")),
            deltStatus = DeltStatus.valueOf(rs.getString("delt_status")),
            deltTidspunkt = rs.getTimestamp("delt_tidspunkt").toLocalDateTime(),
            deltAv = rs.getString("delt_av"),
            svar = Svar.valueOf(rs.getString("svar")),
            svarTidspunkt = rs.getTimestamp("svar_tidspunkt")?.toLocalDateTime(),
            sendtTilKafkaTidspunkt = rs.getTimestamp("sendt_til_kafka_tidspunkt")?.toLocalDateTime(),
            callId = UUID.fromString(rs.getString("call_id"))
        )
    }

    fun tilKafkamelding(stilling: EsStilling): ForesporselOmDelingAvCvKafkamelding {
        return ForesporselOmDelingAvCvKafkamelding(
            aktørId,
            stillingsId.toString(),
            deltAv,
            deltTidspunkt.toInstant(ZoneOffset.UTC),
            callId.toString(),
            "todo",
            "todo",
            "todo",
            listOf(Arbeidssted(
                "todo",
                "todo",
                "todo",
                "todo",
                "todo",
                "todo"
            )),
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
