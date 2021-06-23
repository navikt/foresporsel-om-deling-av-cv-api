import mottasvar.Svar
import no.nav.rekrutteringsbistand.avro.Arbeidssted
import no.nav.rekrutteringsbistand.avro.ForesporselOmDelingAvCv
import stilling.Stilling
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

data class Forespørsel(
    val id: Long,
    val aktørId: String,
    val stillingsId: UUID,

    val deltStatus: DeltStatus,
    val deltTidspunkt: LocalDateTime,
    val deltAv: String,
//    val svarfrist: LocalDate,

    val svar: Svar,
    val svarTidspunkt: LocalDateTime?,

    val sendtTilKafkaTidspunkt: LocalDateTime?,
    val callId: UUID,
) {
    companion object {
        fun fromDb(rs: ResultSet) = Forespørsel(
            id = rs.getLong("id"),
            aktørId = rs.getString("aktor_id"),
            stillingsId = UUID.fromString(rs.getString("stilling_id")),
            deltStatus = DeltStatus.valueOf(rs.getString("delt_status")),
            deltTidspunkt = rs.getTimestamp("delt_tidspunkt").toLocalDateTime(),
            deltAv = rs.getString("delt_av"),
//            svarfrist = rs.getTimestamp("svarfrist").toLocalDateTime().toLocalDate(),
//            svarfrist = LocalDate.now(), // TODO
            svar = Svar.valueOf(rs.getString("svar")),
            svarTidspunkt = rs.getTimestamp("svar_tidspunkt")?.toLocalDateTime(),
            sendtTilKafkaTidspunkt = rs.getTimestamp("sendt_til_kafka_tidspunkt")?.toLocalDateTime(),
            callId = UUID.fromString(rs.getString("call_id"))
        )
    }

    fun tilKafkamelding(stilling: Stilling) = ForesporselOmDelingAvCv(
        aktørId,
        stillingsId.toString(),
        deltAv,
        deltTidspunkt.toInstant(ZoneOffset.UTC),
        callId.toString(),
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

    fun tilOutboundDto() = ForespørselOutboundDto(aktørId, deltStatus, deltTidspunkt, deltAv, svar, svarTidspunkt)
}

enum class DeltStatus {
    SENDT,
    IKKE_SENDT,
}
