package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.ConstructorDetector;
import tools.jackson.datatype.hibernate7.Hibernate7Module;

@Profile(PROFILE_CORE)
@Configuration
// NOTE: Do NOT add @Lazy to this class. The Jackson modules must be
// available when Spring Boot's JacksonAutoConfiguration creates the JsonMapper. With @Lazy, the modules
// are not registered, causing "No _valueDeserializer assigned" errors when deserializing nested entities.
public class JacksonConfiguration {

    /**
     * Support for Hibernate types in Jackson 3.
     * JavaTimeModule is built into Jackson 3 databind and auto-registered — no explicit bean needed.
     */
    @Bean
    public Hibernate7Module hibernateModule() {
        Hibernate7Module module = new Hibernate7Module();
        // Do not serialize lazy-loaded proxies — they should not appear in JSON responses.
        // FORCE_LAZY_LOADING=false (default) means uninitialized lazy proxies are serialized as null.
        module.disable(Hibernate7Module.Feature.FORCE_LAZY_LOADING);
        // Write lazy-not-loaded objects as null to avoid "_valueDeserializer assigned" errors
        module.enable(Hibernate7Module.Feature.WRITE_MISSING_ENTITIES_AS_NULL);
        return module;
    }

    /**
     * Customize the Jackson 3 JsonMapper to match Jackson 2 behavior for entity deserialization.
     * Jackson 3 is stricter about constructor detection — this ensures default (no-arg) constructors
     * are used for entity deserialization, matching the Jackson 2 behavior that Spring MVC relies on.
     */
    @Bean
    public JsonMapperBuilderCustomizer artemisJsonMapperCustomizer() {
        return builder -> builder.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).enable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
                .enable(DeserializationFeature.EAGER_DESERIALIZER_FETCH).constructorDetector(ConstructorDetector.DEFAULT);
    }
}
