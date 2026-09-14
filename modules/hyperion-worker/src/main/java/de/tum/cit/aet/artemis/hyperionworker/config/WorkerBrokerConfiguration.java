package de.tum.cit.aet.artemis.hyperionworker.config;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Requires verified TLS and scoped credentials before any worker broker connection is attempted. */
@Configuration(proxyBeanMethods = false)
public class WorkerBrokerConfiguration {

    @Bean(destroyMethod = "close")
    public ActiveMQConnectionFactory workerConnectionFactory(@Value("${spring.artemis.broker-url}") String url, @Value("${spring.artemis.user}") String user,
            @Value("${spring.artemis.password}") String password) {
        URI broker = URI.create(url);
        Map<String, String> options = new HashMap<>();
        if (broker.getRawQuery() != null) {
            for (String option : broker.getRawQuery().split("[&;]")) {
                String[] pair = option.split("=", 2);
                if (pair.length != 2 || options.putIfAbsent(pair[0], pair[1]) != null) {
                    throw new IllegalArgumentException("Configure unambiguous Hyperion broker TLS options");
                }
            }
        }
        if (!"tcp".equals(broker.getScheme()) || broker.getHost() == null || broker.getUserInfo() != null || broker.getFragment() != null
                || !"true".equals(options.get("sslEnabled")) || !"false".equals(options.getOrDefault("trustAll", "false"))
                || !"true".equals(options.getOrDefault("verifyHost", "true")) || user.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException("Configure a verified TLS Hyperion broker and scoped credentials");
        }
        var factory = new ActiveMQConnectionFactory(url, user, password);
        factory.setCallTimeout(5_000);
        factory.setCallFailoverTimeout(5_000);
        factory.setBlockOnDurableSend(true);
        return factory;
    }
}
