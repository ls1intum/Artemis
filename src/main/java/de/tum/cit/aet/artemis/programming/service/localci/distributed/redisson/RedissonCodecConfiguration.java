package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.config.RedisCondition;

@Lazy
@Configuration
@Conditional(RedisCondition.class)
public class RedissonCodecConfiguration {

    @Bean
    public RedissonAutoConfigurationCustomizer redissonAutoConfigurationCustomizer(ObjectMapper objectMapper) {
        return (Config configuration) -> configuration.setCodec(new JsonJacksonCodec(objectMapper));
    }
}
