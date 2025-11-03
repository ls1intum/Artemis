package de.tum.cit.aet.artemis.core.service.connectors;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.RedisCondition;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson.RedisClientListResolver;

/**
 * Health indicator for the Redis backend used by LocalCI.
 */
@Lazy
@Conditional(RedisCondition.class)
@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    private final RedisClientListResolver redisClientListResolver;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.client-name}")
    private String redisClientName;

    public RedisHealthIndicator(RedisConnectionFactory redisConnectionFactory, RedisClientListResolver redisClientListResolver) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.redisClientListResolver = redisClientListResolver;
    }

    @Override
    public Health health() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {

            String ping = connection.ping();
            if (ping == null) {
                return Health.down().withDetail("ping", "Redis ping failed").build();
            }

            Set<String> uniqueClients = redisClientListResolver.getUniqueClients();

            return Health.up().withDetail("Address", "redis://" + redisHost + ":" + redisPort).withDetail("Ping", "Redis is up")
                    .withDetail("Unique Artemis clients", uniqueClients.size()).withDetail("Artemis Clients", uniqueClients).withDetail("This node client name", redisClientName)
                    .build();

        }
        catch (Exception e) {
            return Health.down(e).withDetail("error", e.getMessage()).build();
        }
    }
}
