package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;

import de.tum.cit.aet.artemis.core.config.RedissonCodecConfiguration;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedisClientListResolver;
import de.tum.cit.aet.artemis.core.service.distributed.redisson.RedissonDistributedDataProviderService;

final class GenerationRedisTestSupport {

    private GenerationRedisTestSupport() {
    }

    static RedissonDistributedDataProviderService provider(RedissonClient redis) {
        return new RedissonDistributedDataProviderService(redis, new RedisClientListResolver(new RedissonConnectionFactory(redis)));
    }

    static RedissonClient client(String address, String name) {
        var config = new Config();
        config.useSingleServer().setAddress(address).setClientName(name).setConnectionMinimumIdleSize(1).setConnectionPoolSize(2).setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(2);
        new RedissonCodecConfiguration().artemisRedissonSerializationCodecCustomizer().customize(config);
        return Redisson.create(config);
    }
}
