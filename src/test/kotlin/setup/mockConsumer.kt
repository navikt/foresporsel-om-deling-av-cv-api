package setup

import mottasvar.svarTopic
import no.nav.rekrutteringsbistand.avro.SvarPaForesporselOmDelingAvCv
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy

fun mockConsumer() = MockConsumer<String, SvarPaForesporselOmDelingAvCv>(OffsetResetStrategy.EARLIEST).apply {
    schedulePollTask {
        rebalance(listOf(svarTopic))
        updateBeginningOffsets(mapOf(Pair(svarTopic, 0)))
    }
}

fun mottaSvarKafkamelding(consumer: MockConsumer<String, SvarPaForesporselOmDelingAvCv>, melding: SvarPaForesporselOmDelingAvCv, offset: Long = 0) {
    val melding = ConsumerRecord(
        svarTopic.topic(),
        svarTopic.partition(),
        offset,
        melding.getAktorId(),
        melding,
    )

    consumer.schedulePollTask {
        consumer.addRecord(melding)
    }
}
