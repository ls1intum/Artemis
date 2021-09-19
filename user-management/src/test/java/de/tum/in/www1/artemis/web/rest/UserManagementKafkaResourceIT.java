package de.tum.in.www1.artemis.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.KafkaContainer;

import de.tum.in.www1.artemis.config.KafkaProperties;

class UserManagementKafkaResourceIT {

    private static boolean started = false;

    private static KafkaContainer kafkaContainer;

    private MockMvc restMockMvc;

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

        UserManagementKafkaResource kafkaResource = new UserManagementKafkaResource(kafkaProperties);

        restMockMvc = MockMvcBuilders.standaloneSetup(kafkaResource).build();
    }

    @Test
    void producesMessages() throws Exception {
        restMockMvc.perform(post("/api/user-management-kafka/publish/topic-produce?message=value-produce")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        Map<String, Object> consumerProps = new HashMap<>(getConsumerProps("group-produce"));
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList("topic-produce"));
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));

        assertThat(records.count()).isEqualTo(1);
        ConsumerRecord<String, String> record = records.iterator().next();
        assertThat(record.value()).isEqualTo("value-produce");
    }

    @Test
    void consumesMessages() throws Exception {
        Map<String, Object> producerProps = new HashMap<>(getProducerProps());
        KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps);

        producer.send(new ProducerRecord<>("topic-consume", "value-consume"));

        MvcResult mvcResult = restMockMvc.perform(get("/api/user-management-kafka/consume?topic=topic-consume")).andExpect(status().isOk()).andExpect(request().asyncStarted())
                .andReturn();

        for (int i = 0; i < 100; i++) {
            Thread.sleep(100);
            String content = mvcResult.getResponse().getContentAsString();
            if (content.contains("data:value-consume")) {
                return;
            }
        }
        fail("Expected content data:value-consume not received");
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
