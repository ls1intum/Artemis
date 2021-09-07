package de.tum.in.www1.artemis.web.rest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.KafkaProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;

@RestController
@RequestMapping("/api/gateway-kafka")
public class GatewayKafkaResource {

    private final Logger log = LoggerFactory.getLogger(GatewayKafkaResource.class);

    private final KafkaProperties kafkaProperties;

    private KafkaSender<String, String> sender;

    public GatewayKafkaResource(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
        this.sender = KafkaSender.create(SenderOptions.create(kafkaProperties.getProducerProps()));
    }

    @PostMapping("/publish/{topic}")
    public Mono<PublishResult> publish(@PathVariable String topic, @RequestParam String message, @RequestParam(required = false) String key) {
        log.debug("REST request to send to Kafka topic {} with key {} the message : {}", topic, key, message);
        return Mono.just(SenderRecord.create(topic, null, null, key, message, null)).as(sender::send).next().map(SenderResult::recordMetadata)
                .map(metadata -> new PublishResult(metadata.topic(), metadata.partition(), metadata.offset(), Instant.ofEpochMilli(metadata.timestamp())));
    }

    @GetMapping("/consume")
    public Flux<String> consume(@RequestParam("topic") List<String> topics, @RequestParam Map<String, String> consumerParams) {
        log.debug("REST request to consume records from Kafka topics {}", topics);
        Map<String, Object> consumerProps = kafkaProperties.getConsumerProps();
        consumerProps.putAll(consumerParams);
        consumerProps.remove("topic");

        ReceiverOptions<String, String> receiverOptions = ReceiverOptions.<String, String>create(consumerProps).subscription(topics);
        return KafkaReceiver.create(receiverOptions).receive().map(ConsumerRecord::value);
    }

    private static class PublishResult {

        public final String topic;

        public final int partition;

        public final long offset;

        public final Instant timestamp;

        private PublishResult(String topic, int partition, long offset, Instant timestamp) {
            this.topic = topic;
            this.partition = partition;
            this.offset = offset;
            this.timestamp = timestamp;
        }
    }
}
