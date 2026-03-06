package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.core.config.RedisCondition;

@Lazy
@Configuration
@Conditional(RedisCondition.class)
public class RedissonCodecConfiguration {

    @Bean
    public RedissonAutoConfigurationCustomizer redissonAutoConfigurationCustomizer() {
        ObjectMapper redissonObjectMapper = new ObjectMapper();
        redissonObjectMapper.registerModule(new JavaTimeModule());
        return (Config configuration) -> configuration.setCodec(new JsonJacksonCodec(redissonObjectMapper));
    }
}
