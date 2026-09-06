package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import tools.jackson.datatype.hibernate7.Hibernate7Module;

@Profile(PROFILE_CORE)
@Configuration
// NOTE: Do NOT add @Lazy to this class. The Jackson modules must be
// available when Spring Boot's JacksonAutoConfiguration creates the JsonMapper. With @Lazy, the modules
// are not registered, causing "No _valueDeserializer assigned" errors when deserializing nested entities.
public class JacksonConfiguration {

    /**
     * Support for Java date and time API (Jackson 2 only).
     *
     * @return the corresponding Jackson module.
     * @deprecated Jackson 3 has java.time support built into jackson-databind. Removed together with the Jackson 2 mapper.
     */
    @Deprecated(since = "8.4.0", forRemoval = true)
    @Bean
    public com.fasterxml.jackson.datatype.jsr310.JavaTimeModule javaTimeModule() {
        return new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule();
    }

    /**
     * Support for Hibernate types in the Jackson 2 mapper.
     *
     * @return the configured Jackson 2 Hibernate7Module
     * @deprecated superseded by {@link #hibernateJacksonModule()}. Removed together with the Jackson 2 mapper.
     */
    @Deprecated(since = "8.4.0", forRemoval = true)
    @Bean
    public com.fasterxml.jackson.datatype.hibernate7.Hibernate7Module hibernateModule() {
        var module = new com.fasterxml.jackson.datatype.hibernate7.Hibernate7Module();
        module.enable(com.fasterxml.jackson.datatype.hibernate7.Hibernate7Module.Feature.REPLACE_PERSISTENT_COLLECTIONS);
        module.enable(com.fasterxml.jackson.datatype.hibernate7.Hibernate7Module.Feature.WRITE_MISSING_ENTITIES_AS_NULL);
        return module;
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
     * <p>
     * Spring Boot picks up every {@link tools.jackson.databind.JacksonModule} bean and registers it on both the
     * {@code JsonMapper} and the {@code XmlMapper} it builds.
     *
     * @return the configured Hibernate7Module
     */
    @Bean
    public Hibernate7Module hibernateJacksonModule() {
        Hibernate7Module module = new Hibernate7Module();
        module.enable(Hibernate7Module.Feature.REPLACE_PERSISTENT_COLLECTIONS);
        module.enable(Hibernate7Module.Feature.WRITE_MISSING_ENTITIES_AS_NULL);
        return module;
    }

    /**
     * Applies {@link ArtemisJacksonDefaults} to the auto-configured {@code JsonMapper}.
     *
     * @return the customizer applied to the auto-configured JsonMapper builder
     */
    @Bean
    public JsonMapperBuilderCustomizer artemisJacksonDefaultsCustomizer() {
        return ArtemisJacksonDefaults::apply;
    }
}
