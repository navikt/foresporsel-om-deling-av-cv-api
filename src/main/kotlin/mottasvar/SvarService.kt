package mottasvar

import no.nav.rekrutteringsbistand.avro.SvarPaForesporselOmDelingAvCv
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import utils.log
import java.io.Closeable
import java.time.Duration
import java.util.*

val svarTopic = TopicPartition("arbeidsgiver-inkludering.svar-pa-deling-av-cv", 0) // TODO: Bruk topic PTO har opprettet

class SvarService(
    private val consumer: Consumer<String, SvarPaForesporselOmDelingAvCv>,
    private val lagreSvar: (SvarPåForespørsel) -> Unit
): Closeable {
    fun start() {
        try {
            consumer.subscribe(listOf(svarTopic.topic()))
            log.info(
                "Starter å konsumere topic ${svarTopic.topic()} med groupId ${consumer.groupMetadata().groupId()}"
            )

            while (true) {
                val records: ConsumerRecords<String, SvarPaForesporselOmDelingAvCv> =
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

    private fun behandle(svarKafkamelding: SvarPaForesporselOmDelingAvCv) {
        val svar = SvarPåForespørsel(
            UUID.fromString(svarKafkamelding.getForesporselId()),
            Svar.valueOf(svarKafkamelding.getSvar())
        )

        lagreSvar(svar)

        // TODO: Validere svar
        // TODO: Håndtere feil i svar, hverken Ja eller Nei
    }

    override fun close() {
        // Vil kaste WakeupException i konsument slik at den stopper, thread-safe.
        consumer.wakeup()
    }

    private var isOk = true

    fun isOk() = isOk
}