package de.tum.in.www1.artemis.service.connectors.redis;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

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

            return Health.up().withDetail("Ping", "Redis is up").withDetail("Unique Artemis clients", uniqueClients.size()).withDetail("Artemis Clients", uniqueClients).build();
        }
        catch (Exception e) {
            return Health.down(e).withDetail("error", e.getMessage()).build();
        }
    }
}
