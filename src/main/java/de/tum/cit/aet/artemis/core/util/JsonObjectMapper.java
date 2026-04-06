package de.tum.cit.aet.artemis.core.util;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Provides a shared, pre-configured {@link JsonMapper} for JSON serialization and deserialization.
 * <p>
 * Use this instead of creating {@code new JsonMapper()} instances throughout the codebase.
 * The shared instance includes:
 * <ul>
 * <li>Java 8+ date/time API support (built into Jackson 3)</li>
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

    private static final JsonMapper INSTANCE = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

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
