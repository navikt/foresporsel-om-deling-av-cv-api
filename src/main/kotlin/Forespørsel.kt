import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.Arbeidssted
import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.ForesporselOmDelingAvCv
import no.nav.veilarbaktivitet.stilling_fra_nav.deling_av_cv.KontaktInfo
import stilling.Stilling
import utils.atOslo
import utils.getNullableBoolean
import utils.toUUID
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.*

data class Ident(
    val ident: String,
    val identType: IdentType,
)

data class Svar(
    val harSvartJa: Boolean,
    val svarTidspunkt: LocalDateTime,
    val svartAv: Ident
) {
    companion object {
        fun fraKafkamelding(melding: no.nav.veilarbaktivitet.avro.Svar?): Svar? {
            return if (melding != null) {
                Svar(
                    harSvartJa = melding.getSvar(),
                    svarTidspunkt = melding.getSvarTidspunkt().atOslo().toLocalDateTime(),
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
    val navKontor: String?,
    val svarfrist: ZonedDateTime,

    val tilstand: Tilstand?,
    val svar: Svar?,
    val begrunnelseForAtAktivitetIkkeBleOpprettet: BegrunnelseForAtAktivitetIkkeBleOpprettet?,

    val sendtTilKafkaTidspunkt: LocalDateTime?,
    val callId: String,
) {
    fun harSvartJa() = svar?.harSvartJa == true

    fun venterPåSvar() = tilstand == Tilstand.HAR_VARSLET || tilstand == Tilstand.PROVER_VARSLING

    fun utløpt() = tilstand == Tilstand.AVBRUTT || tilstand == Tilstand.SVARFRIST_UTLOPT

    fun kanIkkeVarsleBruker() = tilstand == Tilstand.KAN_IKKE_VARSLE

    fun tilKafkamelding(stilling: Stilling) = ForesporselOmDelingAvCv(
        forespørselId.toString(),
        aktørId,
        stillingsId.toString(),
        deltAv,
        deltTidspunkt.atOslo().toInstant(),
        svarfrist.toInstant(),
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

    private fun hentKontaktinfoFraStilling(stilling: Stilling): KontaktInfo? =
        stilling.kontaktinfo?.firstOrNull()?.let {
            KontaktInfo(it.navn, it.tittel, it.mobil)
        }

    fun tilOutboundDto() = ForespørselOutboundDto(
        aktørId,
        stillingsId.toString(),
        deltStatus,
        deltTidspunkt,
        deltAv,
        svarfrist,
        tilstand,
        svar,
        begrunnelseForAtAktivitetIkkeBleOpprettet,
        navKontor
    )

    companion object {
        private fun tilstandEllerNull(verdi: String?): Tilstand? {
            return Tilstand.values().firstOrNull { it.name == verdi }
        }

        private fun begrunnelseEllerNull(verdi: String?): BegrunnelseForAtAktivitetIkkeBleOpprettet? {
            return BegrunnelseForAtAktivitetIkkeBleOpprettet.values().firstOrNull { it.name == verdi }
        }

        fun fromDb(rs: ResultSet): Forespørsel {
            val svar = if (rs.getNullableBoolean("svar") == null) null else Svar(
                harSvartJa = rs.getBoolean("svar"),
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
                navKontor = rs.getString("nav_kontor"),
                svarfrist = rs.getTimestamp("svarfrist").toInstant().atOslo(),
                tilstand = tilstandEllerNull(rs.getString("tilstand")),
                svar = svar,
                begrunnelseForAtAktivitetIkkeBleOpprettet = begrunnelseEllerNull(rs.getString("begrunnelse_for_at_aktivitet_ikke_ble_opprettet")),
                sendtTilKafkaTidspunkt = rs.getTimestamp("sendt_til_kafka_tidspunkt")?.toLocalDateTime(),
                callId = rs.getString("call_id"),
            )
        }
    }
}

enum class Tilstand {
    KAN_IKKE_OPPRETTE,
    PROVER_VARSLING,
    HAR_VARSLET,
    KAN_IKKE_VARSLE,
    HAR_SVART,
    AVBRUTT,
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

enum class BegrunnelseForAtAktivitetIkkeBleOpprettet {
    UGYLDIG_OPPFOLGINGSSTATUS,
    UGYLDIG_INPUT;

    companion object {
        fun fraKafkamelding(melding: no.nav.veilarbaktivitet.avro.KanIkkeOppretteBegrunnelse?) =
            if (melding == null) null else valueOf(melding.getBegrunnelse().name)
    }
}
