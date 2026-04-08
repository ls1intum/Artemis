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

    /**
     * Support for Hibernate types in Jackson.
     * <p>
     * Configures the module to safely handle lazy-loaded proxies:
     * <ul>
     * <li>{@code REPLACE_PERSISTENT_COLLECTIONS} converts Hibernate collections to standard
     * Java collections before serialization, so uninitialized proxies become empty
     * collections instead of triggering {@code LazyInitializationException}.</li>
     * <li>{@code WRITE_MISSING_ENTITIES_AS_NULL} serializes unloaded entity proxies
     * ({@code @ManyToOne}, {@code @OneToOne}) as {@code null}.</li>
     * </ul>
     * This is required because {@code spring.jpa.open-in-view} is {@code false}, meaning
     * the Hibernate session is closed before Jackson serializes the response.
     *
     * @return the configured Hibernate7Module
     */
    @Bean
    public Hibernate7Module hibernateModule() {
        Hibernate7Module module = new Hibernate7Module();
        module.enable(Hibernate7Module.Feature.REPLACE_PERSISTENT_COLLECTIONS);
        module.enable(Hibernate7Module.Feature.WRITE_MISSING_ENTITIES_AS_NULL);
        return module;
    }

    /**
     * Exposes a MappingJackson2HttpMessageConverter bean using the auto-configured ObjectMapper.
     * In Spring Boot 4, this converter is no longer directly injectable as a named bean — it's only registered
     * in the converter list. This explicit bean ensures the Hibernate7Module and JavaTimeModule are applied
     * to the converter used for HTTP responses, preventing lazy loading serialization failures.
     *
     * @param objectMapper the auto-configured Jackson ObjectMapper
     * @return the HTTP message converter
     */
    @Bean
    @SuppressWarnings("removal") // MappingJackson2HttpMessageConverter is deprecated but still required for proper Hibernate module registration
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(ObjectMapper objectMapper) {
        return new MappingJackson2HttpMessageConverter(objectMapper);
    }
}
