package setup

import org.apache.kafka.clients.producer.MockProducer

fun mockProducer(): MockProducer<String, String> {
    return MockProducer(true, null, null)
}
