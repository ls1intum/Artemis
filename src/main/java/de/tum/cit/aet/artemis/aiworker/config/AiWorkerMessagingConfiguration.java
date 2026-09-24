package de.tum.cit.aet.artemis.aiworker.config;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;

/** The AI Worker broker is independent of the browser STOMP relay and its credentials. */
@Lazy
@Configuration
@Conditional(AiWorkerEnabled.class)
@EnableConfigurationProperties(AiWorkerProperties.class)
public class AiWorkerMessagingConfiguration {

    /**
     * Creates a bounded, TLS-only AI Worker broker connection.
     *
     * @param properties scoped core credentials and broker URL
     * @return the native connection factory
     */
    @Bean(name = "aiWorkerNativeConnectionFactory", destroyMethod = "close")
    @Lazy
    public ActiveMQConnectionFactory aiWorkerNativeConnectionFactory(AiWorkerProperties properties) {
        var factory = new ActiveMQConnectionFactory(properties.brokerUrl(), properties.user(), properties.password());
        factory.setCallTimeout(5_000);
        factory.setCallFailoverTimeout(5_000);
        factory.setBlockOnDurableSend(true);
        return factory;
    }

    @Bean(name = "aiWorkerConnectionFactory", destroyMethod = "destroy")
    @Lazy
    public org.springframework.jms.connection.CachingConnectionFactory aiWorkerConnectionFactory(
            @org.springframework.beans.factory.annotation.Qualifier("aiWorkerNativeConnectionFactory") ActiveMQConnectionFactory nativeFactory) {
        var factory = new org.springframework.jms.connection.CachingConnectionFactory(nativeFactory);
        factory.setSessionCacheSize(8);
        factory.setCacheConsumers(false);
        return factory;
    }

    @Bean
    @Lazy
    @Profile("!aiworker")
    public WorkerMessageCodecApi aiWorkerMessageCodecApi() {
        return new WorkerMessageCodecApi();
    }
}
