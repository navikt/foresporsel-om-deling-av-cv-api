package setup

import no.nav.rekrutteringsbistand.avro.ForesporselOmDelingAvCvKafkamelding
import no.nav.rekrutteringsbistand.avro.SvarPaDelingAvCvKafkamelding
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.TopicPartition

private val topic = TopicPartition("svar-pa-deling-av-cv", 0)

fun mockConsumer() = MockConsumer<String, SvarPaDelingAvCvKafkamelding>(OffsetResetStrategy.EARLIEST).apply {
    schedulePollTask {
        rebalance(listOf(topic))
        updateBeginningOffsets(mapOf(Pair(topic, 0)))
    }
}

fun mottaSvarKafkamelding(consumer: MockConsumer<String, SvarPaDelingAvCvKafkamelding>, melding: SvarPaDelingAvCvKafkamelding, offset: Long = 0) {
    val melding = ConsumerRecord(
        topic.topic(),
        topic.partition(),
        offset,
        melding.getAktorId(),
        melding,
    )

    consumer.schedulePollTask {
        consumer.addRecord(melding)
    }
}