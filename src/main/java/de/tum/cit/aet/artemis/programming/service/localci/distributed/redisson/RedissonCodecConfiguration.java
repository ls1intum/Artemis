package de.tum.cit.aet.artemis.programming.service.localci.distributed.redisson;

import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.core.config.RedisCondition;

@Configuration
@Conditional(RedisCondition.class)
public class RedissonCodecConfiguration {

    @Bean
    public RedissonAutoConfigurationCustomizer redissonAutoConfigurationCustomizer() {
        ObjectMapper redissonObjectMapper = new ObjectMapper();
        redissonObjectMapper.registerModule(new JavaTimeModule());

        return (Config configuration) -> configuration.setCodec(new JsonJacksonCodec(redissonObjectMapper) {

            @Override
            protected void init(ObjectMapper objectMapper) {
                objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
                // Use EVERYTHING instead of the default NON_FINAL to include Java records (which are final)
                // in type information. Without this, records like BuildAgentInformation are serialized
                // without @class and cannot be deserialized back from Object.class.
                objectMapper.activateDefaultTyping(BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(), ObjectMapper.DefaultTyping.EVERYTHING,
                        JsonTypeInfo.As.PROPERTY);
            }
        });
    }
}
