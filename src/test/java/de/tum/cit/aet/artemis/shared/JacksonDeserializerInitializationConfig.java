package de.tum.cit.aet.artemis.shared;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;

/**
 * Test configuration to eagerly initialize Jackson deserializers.
 * <p>
 * This configuration addresses flaky test failures caused by race conditions in Jackson's
 * deserializer cache population. When tests run concurrently and trigger deserialization
 * of complex entity types for the first time, Jackson may encounter
 * "No _valueDeserializer assigned" errors due to incomplete deserializer initialization
 * — Jackson sets a {@code FailingDeserializer} placeholder during recursive construction
 * to prevent infinite loops, and a parallel lookup that hits the placeholder before the
 * real deserializer replaces it will fail.
 * <p>
 * We force eager deserializer construction at context startup so that the cache is fully
 * populated before any tests run. {@link ObjectMapper#canDeserialize(JavaType)} triggers
 * the same code path as a real deserialization without requiring a valid JSON sample, so
 * it does not depend on the precise field structure of the entity (which can shift).
 * <p>
 * We cover both the bare type and the {@code List<T>} chain because Jackson caches
 * container-of-T deserializers separately from T.
 */
@TestConfiguration
public class JacksonDeserializerInitializationConfig {

    private static final Logger log = LoggerFactory.getLogger(JacksonDeserializerInitializationConfig.class);

    private final ObjectMapper objectMapper;

    public JacksonDeserializerInitializationConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initializeDeserializers() {
        log.debug("Eagerly initializing Jackson deserializers for entity types");

        // Force eager deserializer construction for these entity types (and List<T>).
        // The same cache is shared with the ObjectMapper Spring MVC uses for HTTP body handling.
        primeDeserializer(User.class);
        primeDeserializer(Organization.class);
        primeDeserializer(Course.class);
        primeDeserializer(Exam.class);
        primeDeserializer(Post.class);
        primeDeserializer(TutorialGroup.class);

        log.debug("Successfully initialized Jackson deserializers");
    }

    /**
     * Force-construct the deserializers for both {@code T} and {@code List<T>}.
     * Uses {@link ObjectMapper#canDeserialize(JavaType)} which fully resolves the
     * deserializer chain (every nested property) without needing a JSON sample.
     */
    private void primeDeserializer(Class<?> entityType) {
        try {
            TypeFactory typeFactory = objectMapper.getTypeFactory();
            JavaType bare = typeFactory.constructType(entityType);
            JavaType listOf = typeFactory.constructCollectionType(List.class, entityType);
            objectMapper.canDeserialize(bare);
            objectMapper.canDeserialize(listOf);
        }
        catch (Exception e) {
            log.warn("Failed to pre-initialize {} deserializer: {}", entityType.getSimpleName(), e.getMessage());
        }
    }
}
