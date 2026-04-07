package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.ConstructorDetector;

@Profile(PROFILE_CORE)
@Configuration
// NOTE: Do NOT add @Lazy to this class. Jackson modules and customizers must be available
// when Spring Boot's JacksonAutoConfiguration creates the JsonMapper.
public class JacksonConfiguration {

    // Hibernate7Module is auto-discovered by Jackson 3 via META-INF/services/tools.jackson.databind.JacksonModule.
    // Spring Boot's JacksonAutoConfiguration calls findAndAddModules() which registers it automatically.
    // Do NOT declare a @Bean here — that would cause double registration.

    /**
     * Customize the Jackson 3 JsonMapper to match Jackson 2 behavior for entity deserialization.
     * Jackson 3 is stricter about constructor detection and null handling for primitives.
     */
    @Bean
    public JsonMapperBuilderCustomizer artemisJsonMapperCustomizer() {
        return builder -> builder.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).enable(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)
                .enable(DeserializationFeature.EAGER_DESERIALIZER_FETCH).constructorDetector(ConstructorDetector.DEFAULT);
    }
}
