package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.fasterxml.jackson.datatype.hibernate7.Hibernate7Module;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.ConstructorDetector;

@Profile(PROFILE_CORE)
@Configuration
// NOTE: Do NOT add @Lazy to this class. Jackson modules and customizers must be available
// when Spring Boot's JacksonAutoConfiguration creates the JsonMapper.
public class JacksonConfiguration {

    /**
     * Support for Hibernate types in Jackson.
     * Uses the Jackson 2 Hibernate7Module (com.fasterxml.jackson.datatype:jackson-datatype-hibernate7)
     * because the Jackson 3 version (tools.jackson.datatype:jackson-datatype-hibernate7:3.1.0) has a bug
     * where its AnnotationIntrospector causes "_valueDeserializer assigned" errors when many entity
     * types are processed in a single JVM. The Jackson 2 module is compatible with Jackson 3's JsonMapper.
     *
     * @see <a href="https://github.com/FasterXML/jackson-datatype-hibernate/issues">Jackson Hibernate Module Issues</a>
     */
    @Bean
    public Hibernate7Module hibernateModule() {
        return new Hibernate7Module();
    }

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
