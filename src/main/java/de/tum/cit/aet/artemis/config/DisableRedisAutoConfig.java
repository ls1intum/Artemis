package de.tum.cit.aet.artemis.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.core.config.NotRedisCondition;

@Lazy
@Conditional(NotRedisCondition.class)
@Configuration
@EnableAutoConfiguration(exclude = { org.redisson.spring.starter.RedissonAutoConfigurationV2.class, org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class })
public class DisableRedisAutoConfig {
}
