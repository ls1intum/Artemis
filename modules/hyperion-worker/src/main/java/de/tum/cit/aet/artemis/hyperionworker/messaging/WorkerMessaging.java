package de.tum.cit.aet.artemis.hyperionworker.messaging;

import jakarta.jms.ConnectionFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

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

    @Bean
    public WorkerEventPublisher workerEventPublisher(ConnectionFactory connectionFactory, WorkerSettings settings, WorkerMessageCodec codec) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setExplicitQosEnabled(true);
        template.setDeliveryPersistent(true);
        return event -> template.convertAndSend("hyperion.worker." + settings.id() + ".events", codec.encode(event));
    }
}
