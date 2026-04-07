package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate7.Hibernate7Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Profile(PROFILE_CORE)
@Configuration
// NOTE: Do NOT add @Lazy to this class. The Jackson modules must be
// available when Spring Boot's JacksonAutoConfiguration creates the ObjectMapper. With @Lazy, the modules
// are not registered, causing "No _valueDeserializer assigned" errors when deserializing nested entities.
public class JacksonConfiguration {

    /**
     * Support for Java date and time API.
     *
     * @return the corresponding Jackson module.
     */
    @Bean
    public JavaTimeModule javaTimeModule() {
        return new JavaTimeModule();
    }

    /*
     * Support for Hibernate types in Jackson.
     */
    @Bean
    public Hibernate7Module hibernateModule() {
        return new Hibernate7Module();
    }

    /**
     * Provides a MappingJackson2HttpMessageConverter that uses the auto-configured ObjectMapper
     * (which includes Hibernate7Module and JavaTimeModule). Without this explicit bean, the
     * default converter may not properly handle Hibernate lazy-loaded collections, causing
     * HttpMessageNotWritableException during JSON serialization.
     *
     * @param objectMapper the auto-configured Jackson ObjectMapper
     * @return the HTTP message converter
     */
    @SuppressWarnings("removal") // Blocked by Jackson 2→3 migration; requires JacksonJsonHttpMessageConverter + JsonMapper
    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(ObjectMapper objectMapper) {
        return new MappingJackson2HttpMessageConverter(objectMapper);
    }
}
