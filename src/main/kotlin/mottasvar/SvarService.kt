package mottasvar

import no.nav.rekrutteringsbistand.avro.SvarPaDelingAvCvKafkamelding
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import utils.log
import java.io.Closeable
import java.time.Duration

val svarTopic = TopicPartition("svar-pa-deling-av-cv", 0)

class SvarService(
    private val consumer: Consumer<String, SvarPaDelingAvCvKafkamelding>,
): Closeable {
    fun start() {
        try {
            consumer.subscribe(listOf(svarTopic.topic()))
            log.info(
                "Starter å konsumere topic ${svarTopic.topic()} med groupId ${consumer.groupMetadata().groupId()}"
            )

            while (true) {
                val records: ConsumerRecords<String, SvarPaDelingAvCvKafkamelding> =
                    consumer.poll(Duration.ofSeconds(5))
                if (records.count() == 0) continue

                records.map { it.value() }
                    .forEach { behandleSvar(it) }

                consumer.commitSync()

                log.info("Committet offset ${records.last().offset()} til Kafka")
            }
        } catch (exception: WakeupException) {
            log.info("Fikk beskjed om å lukke consument med groupId ${consumer.groupMetadata().groupId()}")
        } catch (exception: Exception) {
            // TODO: Implementer feilhåndtering
        } finally {
            consumer.close()
        }
        App.Liveness.kill()
    }

    private fun behandleSvar(svar: SvarPaDelingAvCvKafkamelding) {
        log.info("Behandler svar: $svar")
    }

    override fun close() {
        // Vil kaste WakeupException i konsument slik at den stopper, thread-safe.
        consumer.wakeup()
    }
}