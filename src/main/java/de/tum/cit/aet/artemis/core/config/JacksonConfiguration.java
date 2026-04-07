package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

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

}
