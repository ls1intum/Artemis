package de.tum.cit.aet.artemis.hyperion.config;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.hyperion.protocol.WorkerMessageCodec;

/** The generation broker is independent of the browser STOMP relay and its credentials. */
@Lazy
@Configuration
@Conditional(HyperionExerciseGenerationEnabled.class)
@EnableConfigurationProperties(HyperionWorkerProperties.class)
public class HyperionWorkerMessagingConfiguration {

    /**
     * Creates a bounded, TLS-only generation broker connection.
     *
     * @param properties scoped core credentials and broker URL
     * @return the native connection factory
     */
    @Bean(name = "hyperionNativeConnectionFactory", destroyMethod = "close")
    @Lazy
    public ActiveMQConnectionFactory hyperionNativeConnectionFactory(HyperionWorkerProperties properties) {
        var factory = new ActiveMQConnectionFactory(properties.brokerUrl(), properties.user(), properties.password());
        factory.setCallTimeout(5_000);
        factory.setCallFailoverTimeout(5_000);
        factory.setBlockOnDurableSend(true);
        return factory;
    }

    @Bean(name = "hyperionConnectionFactory", destroyMethod = "destroy")
    @Lazy
    public org.springframework.jms.connection.CachingConnectionFactory hyperionConnectionFactory(
            @org.springframework.beans.factory.annotation.Qualifier("hyperionNativeConnectionFactory") ActiveMQConnectionFactory nativeFactory) {
        var factory = new org.springframework.jms.connection.CachingConnectionFactory(nativeFactory);
        factory.setSessionCacheSize(8);
        factory.setCacheConsumers(false);
        return factory;
    }

    @Bean
    @Lazy
    public WorkerMessageCodec hyperionWorkerMessageCodec() {
        return new WorkerMessageCodec();
    }
}
