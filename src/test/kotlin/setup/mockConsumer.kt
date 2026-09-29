package setup

import mottasvar.svarTopic
import no.nav.veilarbaktivitet.avro.DelingAvCvRespons
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.internals.AutoOffsetResetStrategy.StrategyType

fun mockConsumer() = MockConsumer<String, DelingAvCvRespons>(StrategyType.EARLIEST.toString()).apply {
    schedulePollTask {
        rebalance(listOf(svarTopic))
        updateBeginningOffsets(mapOf(Pair(svarTopic, 0)))
    }
}

fun mottaSvarKafkamelding(consumer: MockConsumer<String, DelingAvCvRespons>, melding: DelingAvCvRespons, offset: Long = 0) {
    val record = ConsumerRecord(
        svarTopic.topic(),
        svarTopic.partition(),
        offset,
        melding.getBestillingsId(),
        melding,
    )

    consumer.schedulePollTask {
        consumer.addRecord(record)
    }
}
