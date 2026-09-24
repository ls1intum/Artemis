package de.tum.cit.aet.artemis.aiworker.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_AIWORKER;

import java.time.Duration;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Message;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;
import de.tum.cit.aet.artemis.aiworker.service.messaging.WorkerEventPublisher;

/** Only the worker's own command and event destinations are used; broker ACLs enforce the boundary. */
@Configuration(proxyBeanMethods = false)
@Profile(PROFILE_AIWORKER)
@Lazy
@EnableConfigurationProperties(WorkerSettings.class)
@EnableJms
@EnableScheduling
public class WorkerMessaging {

    @Bean
    public WorkerMessageCodecApi workerMessageCodec() {
        return new WorkerMessageCodecApi();
    }

    /**
     * Acknowledges a command after local admission, never after the long-running model session.
     *
     * @param connectionFactory scoped broker connection
     * @return a single transactional command consumer
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(@Qualifier("workerConnectionFactory") ConnectionFactory connectionFactory) {
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
    public WorkerEventPublisher workerEventPublisher(@Qualifier("workerConnectionFactory") ConnectionFactory connectionFactory, WorkerSettings settings,
            WorkerMessageCodecApi codec) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        return event -> template.execute(session -> {
            var message = session.createTextMessage(codec.encode(event));
            message.setStringProperty("eventType", event.type().name());
            if (event.identity() != null) {
                message.setStringProperty("executionId", event.identity().executionId().toString());
            }
            long lifetime = event.type() == WorkerEventType.HEARTBEAT ? Duration.ofSeconds(10).toMillis() : 0;
            try (var producer = session.createProducer(session.createQueue("aiworker." + settings.id() + ".events"))) {
                producer.send(message, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY, lifetime);
            }
            return null;
        });
    }
}
