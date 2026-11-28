package de.tum.cit.aet.artemis.core.service.connectors;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.RedisDistributedDataCondition;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;

/**
 * Health indicator for the configured Redis data provider.
 */
@Lazy
@Conditional(RedisDistributedDataCondition.class)
@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    private final RedisClientListResolver redisClientListResolver;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    private final DistributedDataProvider provider;

    public RedisHealthIndicator(RedisConnectionFactory redisConnectionFactory, RedisClientListResolver redisClientListResolver, DistributedDataProvider provider) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.redisClientListResolver = redisClientListResolver;
        this.provider = provider;
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
                    .withDetail("Unique Artemis clients", uniqueClients.size()).withDetail("Artemis Clients", uniqueClients)
                    .withDetail("This node client name", provider.getLocalMemberAddress()).build();

        }
        catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
