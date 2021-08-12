package mottasvar

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer

val consumerConfig = mapOf(
    CommonClientConfigs.CLIENT_ID_CONFIG to "foresporsel-om-deling-av-cv-api",
    CommonClientConfigs.GROUP_ID_CONFIG to "foresporsel-om-deling-av-cv-api-1",
    CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to System.getenv("KAFKA_BROKERS"),

    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java.name,

    KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to System.getenv("KAFKA_SCHEMA_REGISTRY"),
    KafkaAvroDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE to "USER_INFO",
    KafkaAvroDeserializerConfig.USER_INFO_CONFIG to "${System.getenv("KAFKA_SCHEMA_REGISTRY_USER")}:${System.getenv("KAFKA_SCHEMA_REGISTRY_PASSWORD")}",

    CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SSL",
    SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to System.getenv("KAFKA_KEYSTORE_PATH"),
    SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to System.getenv("KAFKA_CREDSTORE_PASSWORD"),
    SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to System.getenv("KAFKA_CREDSTORE_PASSWORD"),
    SslConfigs.SSL_KEY_PASSWORD_CONFIG to System.getenv("KAFKA_CREDSTORE_PASSWORD"),
    SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "JKS",
    SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12",
    SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to System.getenv("KAFKA_TRUSTSTORE_PATH"),
).toProperties()
