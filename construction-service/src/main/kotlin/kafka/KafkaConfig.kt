package org.burgas.kafka

import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.github.flaxoos.ktor.server.plugins.kafka.common
import io.github.flaxoos.ktor.server.plugins.kafka.installKafka
import io.github.flaxoos.ktor.server.plugins.kafka.producer
import io.ktor.server.application.*
import org.apache.kafka.common.serialization.StringSerializer

fun Application.configureKafka() {
    installKafka {

        schemaRegistryUrl = "http://localhost:8081"

        common {
            bootstrapServers = listOf("localhost:9092")
        }

        producer {
            keySerializerClass = StringSerializer::class.java
            valueSerializerClass = KafkaAvroSerializer::class.java
        }
    }
}