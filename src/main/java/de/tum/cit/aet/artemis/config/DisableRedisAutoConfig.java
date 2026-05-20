package de.tum.cit.aet.artemis.config;

import org.redisson.spring.starter.RedissonAutoConfigurationV2;
import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.core.config.NotRedisCondition;

@Lazy
@Conditional(NotRedisCondition.class)
@Configuration
@EnableAutoConfiguration(exclude = { RedissonAutoConfigurationV2.class, RedissonAutoConfigurationV4.class, DataRedisAutoConfiguration.class,
        DataRedisReactiveAutoConfiguration.class, DataRedisRepositoriesAutoConfiguration.class })
public class DisableRedisAutoConfig {
}
