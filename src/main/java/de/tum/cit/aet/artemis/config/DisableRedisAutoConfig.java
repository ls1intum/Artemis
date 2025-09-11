package de.tum.cit.aet.artemis.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Lazy
@ConditionalOnExpression("'${artemis.continuous-integration.data-store:Hazelcast}' != 'Redis'")
@Configuration
@EnableAutoConfiguration(exclude = { org.redisson.spring.starter.RedissonAutoConfigurationV2.class, org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class })
public class DisableRedisAutoConfig {
}
