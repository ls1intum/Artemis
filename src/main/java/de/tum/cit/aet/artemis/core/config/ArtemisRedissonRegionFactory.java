package de.tum.cit.aet.artemis.core.config;

import java.util.Map;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.Kryo5Codec;
import org.redisson.config.Config;
import org.redisson.config.TransportMode;
import org.redisson.hibernate.RedissonRegionNativeFactory;
import org.springframework.core.env.Environment;

import de.tum.cit.aet.artemis.ArtemisApp;

public class ArtemisRedissonRegionFactory extends RedissonRegionNativeFactory {

    @Override
    protected RedissonClient createRedissonClient(StandardServiceRegistry registry, Map properties) {
        Environment env = ArtemisApp.env;

        Config config = new Config();
        // Configure the Redisson client using the properties
        String address = "redis://" + env.getRequiredProperty("spring.data.redis.host") + ":" + env.getRequiredProperty("spring.data.redis.port");

        // TODO: allow setting other configuration options
        config.useSingleServer().setAddress(address).setUsername(env.getRequiredProperty("spring.data.redis.username"))
                .setPassword(env.getRequiredProperty("spring.data.redis.password")).setClientName(env.getRequiredProperty("spring.data.redis.client-name"))
                .setIdleConnectionTimeout(env.getRequiredProperty("spring.data.redis.idle-connection-timeout", Integer.class))
                .setConnectTimeout(env.getRequiredProperty("spring.data.redis.connect-timeout", Integer.class))
                .setTimeout(env.getRequiredProperty("spring.data.redis.timeout", Integer.class))
                .setRetryAttempts(env.getRequiredProperty("spring.data.redis.retry-attempts", Integer.class))
                .setRetryInterval(env.getRequiredProperty("spring.data.redis.retry-interval", Integer.class))
                .setSubscriptionsPerConnection(env.getRequiredProperty("spring.data.redis.subscriptions-per-connection", Integer.class))
                .setSubscriptionConnectionMinimumIdleSize(env.getRequiredProperty("spring.data.redis.subscription-connection-minimum-idle-size", Integer.class))
                .setSubscriptionConnectionPoolSize(env.getRequiredProperty("spring.data.redis.subscription-connection-pool-size", Integer.class))
                .setConnectionMinimumIdleSize(env.getRequiredProperty("spring.data.redis.connection-minimum-idle-size", Integer.class))
                .setConnectionPoolSize(env.getRequiredProperty("spring.data.redis.connection-pool-size", Integer.class))
                .setDatabase(env.getRequiredProperty("spring.data.redis.database", Integer.class))
                .setDnsMonitoringInterval(env.getRequiredProperty("spring.data.redis.dns-monitoring-interval", Integer.class));

        config.setThreads(env.getRequiredProperty("spring.data.redis.threads", Integer.class));
        config.setNettyThreads(env.getRequiredProperty("spring.data.redis.netty-threads", Integer.class));
        // TODO: read from string
        config.setTransportMode(TransportMode.NIO);

        try {
            Class<?> codecClass = Class.forName(env.getRequiredProperty("spring.data.redis.codec"));
            config.setCodec((org.redisson.client.codec.Codec) codecClass.getDeclaredConstructor().newInstance());
        }
        catch (Exception e) {
            config.setCodec(new Kryo5Codec());
        }

        return Redisson.create(config);
    }
}
