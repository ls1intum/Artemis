package de.tum.cit.aet.artemis.core.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Provides a shared, pre-configured {@link ObjectMapper} for JSON serialization and deserialization.
 * <p>
 * Use this instead of creating {@code new ObjectMapper()} instances throughout the codebase.
 * The shared instance includes:
 * <ul>
 * <li>{@link JavaTimeModule} for Java 8+ date/time API support</li>
 * <li>{@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES} disabled (matching Spring Boot defaults)</li>
 * </ul>
 * <p>
 * <b>When to use:</b>
 * <ul>
 * <li>For Spring-managed beans (services, controllers), prefer injecting {@code ObjectMapper} directly.</li>
 * <li>For static contexts, domain classes, DTOs, JPA converters, and tests where injection
 * is not available, use {@code JsonObjectMapper.get()}.</li>
 * </ul>
 */
public final class JsonObjectMapper {

    private static final ObjectMapper INSTANCE = new ObjectMapper().registerModule(new JavaTimeModule()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonObjectMapper() {
    }

    /**
     * Returns the shared, pre-configured ObjectMapper instance.
     *
     * @return the shared ObjectMapper
     */
    public static ObjectMapper get() {
        return INSTANCE;
    }
}
