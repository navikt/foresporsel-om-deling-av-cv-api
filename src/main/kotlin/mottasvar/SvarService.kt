package mottasvar

import Repository
import Svar
import Tilstand
import no.nav.veilarbaktivitet.avro.DelingAvCvRespons
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import utils.log
import utils.toUUID
import java.io.Closeable
import java.time.Duration

val svarTopic = TopicPartition("pto.stilling-fra-nav-oppdatert-v2", 0)

class SvarService(
    private val consumer: Consumer<String, DelingAvCvRespons>,
    private val repository: Repository
): Closeable {
    fun start() {
        try {
            consumer.subscribe(listOf(svarTopic.topic()))
            log.info(
                "Starter å konsumere topic ${svarTopic.topic()} med groupId ${consumer.groupMetadata().groupId()}"
            )

            while (true) {
                val records: ConsumerRecords<String, DelingAvCvRespons> =
                    consumer.poll(Duration.ofSeconds(5))

                if (records.count() == 0) continue
                records.map { it.value() }.forEach {
                    behandle(it)
                }

                consumer.commitSync()

                log.info("Committet offset ${records.last().offset()} til Kafka")
            }
        } catch (exception: WakeupException) {
            log.info("Fikk beskjed om å lukke consument med groupId ${consumer.groupMetadata().groupId()}")
        } catch (exception: Exception) {
            log.error("Feil ved konsumering av svar på forespørsel.",exception)
            isOk = false
        } finally {
            consumer.close()
        }
    }

    private fun behandle(svarKafkamelding: DelingAvCvRespons) {
        val forespørselId = svarKafkamelding.getBestillingsId().toUUID()
        val tilstand = Tilstand.valueOf(svarKafkamelding.getTilstand().toString())
        val svar = Svar.fraKafkamelding(svarKafkamelding.getSvar())
        val begrunnelse = BegrunnelseForAtAktivitetIkkeBleOpprettet.fraKafkamelding(svarKafkamelding.getKanIkkeOppretteBegrunnelse())

        repository.oppdaterMedRespons(forespørselId, tilstand, svar, begrunnelse)

        val svartAv = if (svar?.svartAv?.identType == IdentType.NAV_IDENT) "veileder (${svar.svartAv.ident})" else "brukeren selv"
        log.info("Behandlet svar for forespørsel-ID: ${forespørselId}, tilstand: ${tilstand}, svar: ${svar?.harSvartJa}, svart av $svartAv")

        if (tilstand == Tilstand.KAN_IKKE_OPPRETTE) {
            val feilmelding = svarKafkamelding.getKanIkkeOppretteBegrunnelse().getFeilmelding()
            log.error("Kan ikke opprette aktivitetskort for forespørsel-ID $forespørselId. Begrunnelse: $begrunnelse, Feilmelding: $feilmelding")
        }
    }

    override fun close() {
        // Vil kaste WakeupException i konsument slik at den stopper, thread-safe.
        consumer.wakeup()
    }

    private var isOk = true

    fun isOk() = isOk
}
