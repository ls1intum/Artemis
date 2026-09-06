package de.tum.cit.aet.artemis.core.util;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.ArtemisJacksonDefaults;

/**
 * Provides a shared, pre-configured {@link JsonMapper} for JSON serialization and deserialization.
 * <p>
 * Use this instead of creating {@code new JsonMapper()} instances throughout the codebase.
 * The shared instance includes:
 * <ul>
 * <li>the Artemis defaults from {@link ArtemisJacksonDefaults}, so a value serialized here matches one
 * serialized by a controller</li>
 * <li>{@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES} disabled (matching Spring Boot defaults)</li>
 * </ul>
 * <p>
 * <b>When to use:</b>
 * <ul>
 * <li>For Spring-managed beans (services, controllers), prefer injecting {@code JsonMapper} directly.</li>
 * <li>For static contexts, domain classes, DTOs, JPA converters, and tests where injection
 * is not available, use {@code JsonObjectMapper.get()}.</li>
 * </ul>
 */
public final class JsonObjectMapper {

    // java.time support is built into jackson-databind 3 and needs no module registration
    private static final JsonMapper INSTANCE = ArtemisJacksonDefaults.apply(JsonMapper.builder()).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private JsonObjectMapper() {
    }

    /**
     * Returns the shared, pre-configured JsonMapper instance.
     *
     * @return the shared JsonMapper
     */
    public static JsonMapper get() {
        return INSTANCE;
    }
}
