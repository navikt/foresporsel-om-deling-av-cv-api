import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.Arbeidssted
import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.ForesporselOmDelingAvCv
import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.KontaktInfo
import stilling.Stilling
import utils.getNullableBoolean
import utils.toUUID
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

data class Ident(
    val ident: String,
    val identType: IdentType,
)

data class Svar(
    val svar: Boolean,
    val svarTidspunkt: LocalDateTime,
    val svartAv: Ident
) {
    companion object {
        fun fraKafkamelding(melding: no.nav.veilarbaktivitet.avro.Svar?): Svar? {
            return if (melding != null) {
                Svar(
                    svar = melding.getSvar(),
                    svarTidspunkt = LocalDateTime.ofInstant(melding.getSvarTidspunkt(), ZoneOffset.UTC),
                    svartAv = Ident(
                        ident = melding.getSvartAv().getIdent(),
                        identType = IdentType.valueOf(melding.getSvartAv().getIdentType().toString())
                    )
                )
            } else {
                null
            }
        }
    }
}

data class Forespørsel(
    val id: Long,
    val aktørId: String,
    val stillingsId: UUID,
    val forespørselId: UUID,

    val deltStatus: DeltStatus,
    val deltTidspunkt: LocalDateTime,
    val deltAv: String,
    val svarfrist: LocalDateTime,

    val tilstand: Tilstand?,
    val svar: Svar?,

    val sendtTilKafkaTidspunkt: LocalDateTime?,
    val callId: String,
) {
    companion object {
        private fun tilstandEllerNull(verdi: String?): Tilstand? {
            return Tilstand.values().firstOrNull { it.name == verdi }
        }

        fun fromDb(rs: ResultSet): Forespørsel {
            val svar = if (rs.getNullableBoolean("svar") == null) null else Svar(
                svar = rs.getBoolean("svar"),
                svarTidspunkt = rs.getTimestamp("svar_tidspunkt").toLocalDateTime(),
                svartAv = Ident(
                    ident = rs.getString("svart_av_ident"),
                    identType = IdentType.valueOf(rs.getString("svart_av_ident_type")),
                ),
            )

            return Forespørsel(
                id = rs.getLong("id"),
                aktørId = rs.getString("aktor_id"),
                stillingsId = rs.getString("stilling_id").toUUID(),
                forespørselId = rs.getString("foresporsel_id").toUUID(),
                deltStatus = DeltStatus.valueOf(rs.getString("delt_status")),
                deltTidspunkt = rs.getTimestamp("delt_tidspunkt").toLocalDateTime(),
                deltAv = rs.getString("delt_av"),
                svarfrist = rs.getTimestamp("svarfrist").toLocalDateTime(),
                tilstand = tilstandEllerNull(rs.getString("tilstand")),
                svar = svar,
                sendtTilKafkaTidspunkt = rs.getTimestamp("sendt_til_kafka_tidspunkt")?.toLocalDateTime(),
                callId = rs.getString("call_id")
            )
        }
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
        },
        hentKontaktinfoFraStilling(stilling)
    )

    private fun hentKontaktinfoFraStilling(stilling: Stilling): KontaktInfo {
        val kontaktinformasjon = stilling.contacts?.first()

        return if (kontaktinformasjon == null)
            KontaktInfo("", "", "", "")
        else KontaktInfo(
            kontaktinformasjon.name,
            kontaktinformasjon.title,
            kontaktinformasjon.phone,
            kontaktinformasjon.email
        )
    }

    fun tilOutboundDto() = ForespørselOutboundDto(
        aktørId,
        deltStatus,
        deltTidspunkt,
        deltAv,
        svarfrist,
        tilstand,
        svar
    )
}

enum class Tilstand {
    KAN_IKKE_OPPRETTE,
    PROVER_VARSLING,
    HAR_VARSLET,
    KAN_IKKE_VARSLE,
    HAR_SVART,
    SVARFRIST_UTLOPT
}

enum class IdentType {
    AKTOR_ID,
    NAV_IDENT,
}

enum class DeltStatus {
    SENDT,
    IKKE_SENDT,
}
