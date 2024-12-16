package de.tum.cit.aet.artemis.core.service.connectors.redis;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Service determining the health of the Athena service and its assessment modules.
 */
@Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    private final RedisClientListResolver redisClientListResolver;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.username}")
    private String redisUsername;

    @Value("${spring.data.redis.client-name}")
    private String redisClientName;

    public RedisHealthIndicator(RedisConnectionFactory redisConnectionFactory, RedisClientListResolver redisClientListResolver) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.redisClientListResolver = redisClientListResolver;
    }

    @Override
    public Health health() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            // Check if Redis is up
            String ping = connection.ping();
            if (ping == null) {
                return Health.down().withDetail("ping", "Redis ping failed").build();
            }

            Set<String> uniqueClients = redisClientListResolver.getUniqueClients();

            return Health.up().withDetail("Address", "redis://" + redisHost + ":" + redisPort).withDetail("Ping", "Redis is up")
                    .withDetail("Unique Artemis clients", uniqueClients.size()).withDetail("Artemis Clients", uniqueClients).withDetail("Username", redisUsername)
                    .withDetail("This node client name", redisClientName).build();

        }
        catch (Exception e) {
            return Health.down(e).withDetail("error", e.getMessage()).build();
        }
    }
}
