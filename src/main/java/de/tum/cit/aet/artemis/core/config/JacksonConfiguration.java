package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.jackson.autoconfigure.XmlMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import tools.jackson.datatype.hibernate7.Hibernate7Module;

@Profile(PROFILE_CORE)
@Configuration
// NOTE: Do NOT add @Lazy to this class. The Jackson modules must be
// available when Spring Boot's JacksonAutoConfiguration creates the JsonMapper. With @Lazy, the modules
// are not registered, causing "No _valueDeserializer assigned" errors when deserializing nested entities.
public class JacksonConfiguration {

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
     * <p>
     * The order is load-bearing: Spring's own property-driven customizer runs at order 0, and these defaults have to
     * win over it, so this is pinned to the lowest precedence rather than relying on the unordered default.
     *
     * @return the customizer applied to the auto-configured JsonMapper builder
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public JsonMapperBuilderCustomizer artemisJacksonDefaultsCustomizer() {
        return ArtemisJacksonDefaults::apply;
    }

    /**
     * Applies the same defaults to the auto-configured {@code XmlMapper}.
     * <p>
     * Spring Boot builds one as soon as {@code jackson-dataformat-xml} is on the classpath and registers an XML
     * message converter from it, so without this an {@code Accept: application/xml} request would render enums
     * through {@code toString()} while the JSON of the same object renders them through {@code name()}.
     *
     * @return the customizer applied to the auto-configured XmlMapper builder
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public XmlMapperBuilderCustomizer artemisXmlJacksonDefaultsCustomizer() {
        return ArtemisJacksonDefaults::apply;
    }
}
