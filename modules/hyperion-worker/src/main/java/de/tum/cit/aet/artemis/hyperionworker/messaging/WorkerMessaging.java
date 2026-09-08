package de.tum.cit.aet.artemis.hyperionworker.messaging;

import java.time.Duration;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Message;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import de.tum.cit.aet.artemis.hyperion.protocol.WorkerEvent;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;
import de.tum.cit.aet.artemis.hyperionworker.config.WorkerSettings;

/** Only the worker's own command and event destinations are used; broker ACLs enforce the boundary. */
@Configuration
@EnableJms
@EnableScheduling
public class WorkerMessaging {

    @Bean
    public WorkerMessageCodec workerMessageCodec() {
        return new WorkerMessageCodec();
    }

    /**
     * Acknowledges a command after local admission, never after the long-running model session.
     *
     * @param connectionFactory scoped broker connection
     * @return a single transactional command consumer
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
        var factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionTransacted(true);
        factory.setConcurrency("1");
        return factory;
    }

    /**
     * Publishes durable evidence, with a short expiry for non-replayable heartbeats.
     *
     * @param connectionFactory scoped broker connection
     * @param settings          worker identity
     * @param codec             bounded wire codec
     * @return the authenticated event publisher
     */
    @Bean
    public WorkerEventPublisher workerEventPublisher(ConnectionFactory connectionFactory, WorkerSettings settings, WorkerMessageCodec codec) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        return event -> template.execute(session -> {
            var message = session.createTextMessage(codec.encode(event));
            message.setStringProperty("eventType", event.type().name());
            if (event.identity() != null) {
                message.setStringProperty("executionId", event.identity().executionId().toString());
            }
            long lifetime = event.type() == WorkerEvent.Type.HEARTBEAT ? Duration.ofSeconds(10).toMillis() : Duration.ofHours(12).toMillis();
            try (var producer = session.createProducer(session.createQueue("hyperion.worker." + settings.id() + ".events"))) {
                producer.send(message, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY, lifetime);
            }
            return null;
        });
    }
}
