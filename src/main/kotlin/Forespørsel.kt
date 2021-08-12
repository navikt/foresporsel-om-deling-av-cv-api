import mottasvar.Svar
import no.nav.veilarbaktivitet.avro.Arbeidssted
import no.nav.veilarbaktivitet.avro.ForesporselOmDelingAvCv
import stilling.Stilling
import utils.toUUID
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

data class Forespørsel(
    val id: Long,
    val aktørId: String,
    val stillingsId: UUID,
    val forespørselId: UUID,

    val deltStatus: DeltStatus,
    val deltTidspunkt: LocalDateTime,
    val deltAv: String,
    val svarfrist: LocalDateTime,

    val svar: Svar,
    val svarTidspunkt: LocalDateTime?,

    val sendtTilKafkaTidspunkt: LocalDateTime?,
    val callId: String,
) {
    companion object {
        fun fromDb(rs: ResultSet) = Forespørsel(
            id = rs.getLong("id"),
            aktørId = rs.getString("aktor_id"),
            stillingsId = rs.getString("stilling_id").toUUID(),
            forespørselId = rs.getString("foresporsel_id").toUUID(),
            deltStatus = DeltStatus.valueOf(rs.getString("delt_status")),
            deltTidspunkt = rs.getTimestamp("delt_tidspunkt").toLocalDateTime(),
            deltAv = rs.getString("delt_av"),
            svarfrist = rs.getTimestamp("svarfrist").toLocalDateTime(),
            svar = Svar.valueOf(rs.getString("svar")),
            svarTidspunkt = rs.getTimestamp("svar_tidspunkt")?.toLocalDateTime(),
            sendtTilKafkaTidspunkt = rs.getTimestamp("sendt_til_kafka_tidspunkt")?.toLocalDateTime(),
            callId = rs.getString("call_id")
        )
    }

    fun tilKafkamelding(stilling: Stilling) = ForesporselOmDelingAvCv(
        forespørselId.toString(),
        aktørId,
        stillingsId.toString(),
        deltAv,
        deltTidspunkt.toInstant(ZoneOffset.UTC),
        svarfrist.toInstant(ZoneOffset.UTC),
        callId,
        stilling.stillingtittel,
        stilling.søknadsfrist,
        stilling.arbeidsgiver,
        stilling.arbeidssteder.map { arbeidssted ->
            Arbeidssted(
                arbeidssted.adresse,
                arbeidssted.postkode,
                arbeidssted.by,
                arbeidssted.kommune,
                arbeidssted.fylke,
                arbeidssted.land
            )
        }
    )

    fun tilOutboundDto() = ForespørselOutboundDto(aktørId, deltStatus, deltTidspunkt, deltAv, svarfrist, svar, svarTidspunkt)
}

enum class DeltStatus {
    SENDT,
    IKKE_SENDT,
}
