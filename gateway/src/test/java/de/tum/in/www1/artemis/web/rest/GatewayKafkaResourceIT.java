package de.tum.in.www1.artemis.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.artemis.config.KafkaProperties;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.KafkaContainer;

class GatewayKafkaResourceIT {

    private static boolean started = false;
    private static KafkaContainer kafkaContainer;

    private WebTestClient client;

    @BeforeAll
    static void startServer() {
        if (!started) {
            startTestcontainer();
            started = true;
        }
    }

    private static void startTestcontainer() {
        // TODO: withNetwork will need to be removed soon
        // See discussion at https://github.com/jhipster/generator-jhipster/issues/11544#issuecomment-609065206
        kafkaContainer = new KafkaContainer("5.5.4").withNetwork(null);
        kafkaContainer.start();
    }

    @BeforeEach
    void setup() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        Map<String, String> producerProps = getProducerProps();
        kafkaProperties.setProducer(new HashMap<>(producerProps));

        Map<String, String> consumerProps = getConsumerProps("default-group");
        consumerProps.put("client.id", "default-client");
        kafkaProperties.setConsumer(consumerProps);

        GatewayKafkaResource kafkaResource = new GatewayKafkaResource(kafkaProperties);

        client = WebTestClient.bindToController(kafkaResource).build();
    }

    @Test
    void producesMessages() {
        client
            .post()
            .uri("/api/gateway-kafka/publish/topic-produce?message=value-produce")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON);

        Map<String, Object> consumerProps = new HashMap<>(getConsumerProps("group-produce"));
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList("topic-produce"));
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

        assertThat(records.count()).isEqualTo(1);
        ConsumerRecord<String, String> record = records.iterator().next();
        assertThat(record.value()).isEqualTo("value-produce");
    }

    @Test
    void consumesMessages() {
        Map<String, Object> producerProps = new HashMap<>(getProducerProps());
        KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps);

        producer.send(new ProducerRecord<>("topic-consume", "value-consume"));

        String value = client
            .get()
            .uri("/api/gateway-kafka/consume?topic=topic-consume")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst(Duration.ofSeconds(10));

        assertThat(value).isEqualTo("value-consume");
    }

    private Map<String, String> getProducerProps() {
        Map<String, String> producerProps = new HashMap<>();
        producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put("bootstrap.servers", kafkaContainer.getBootstrapServers());
        return producerProps;
    }

    private Map<String, String> getConsumerProps(String group) {
        Map<String, String> consumerProps = new HashMap<>();
        consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("bootstrap.servers", kafkaContainer.getBootstrapServers());
        consumerProps.put("auto.offset.reset", "earliest");
        consumerProps.put("group.id", group);
        return consumerProps;
    }
}
