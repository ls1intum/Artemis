package de.tum.cit.aet.artemis.shared;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;

/**
 * Test configuration to eagerly initialize Jackson deserializers.
 * <p>
 * This configuration addresses flaky test failures caused by race conditions in Jackson's
 * deserializer cache population. When tests run concurrently and trigger deserialization
 * of complex entity types (like Organization with nested User objects) for the first time,
 * Jackson may encounter "No _valueDeserializer assigned" errors due to incomplete
 * deserializer initialization.
 * <p>
 * By performing dummy deserializations at startup, we ensure the deserializer cache is
 * properly populated before any tests run, eliminating the race condition.
 * <p>
 * Leaf entity types (User, Course) are initialized first, then composite types that
 * reference them (Organization, TutorialGroup). This ordering ensures all referenced
 * deserializers are cached before any composite type attempts to resolve them.
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

        // Phase 1: Initialize leaf entity types first to ensure their deserializers are
        // fully cached before any composite type tries to reference them concurrently.
        initializeType(User.class, """
                {"id": 1, "login": "testuser", "firstName": "Test", "lastName": "User", "email": "test@test.com", "activated": true}
                """);

        initializeType(Course.class, """
                {"id": 1, "title": "Test Course", "shortName": "TC"}
                """);

        initializeType(TutorialGroupRegistration.class, """
                {"id": 1, "type": "INSTRUCTOR_REGISTRATION"}
                """);

        // Phase 2: Initialize composite entity types that reference the leaf types above.
        initializeType(Organization.class, """
                {
                    "id": 1, "name": "Test Org", "shortName": "TO", "emailPattern": ".*@test.com",
                    "users": [{"id": 1, "login": "testuser", "firstName": "Test", "lastName": "User", "email": "test@test.com", "activated": true}],
                    "courses": [{"id": 1, "title": "Test Course", "shortName": "TC"}]
                }
                """);

        initializeType(TutorialGroup.class, """
                {
                    "id": 1, "title": "Test Group",
                    "registrations": [{"id": 1, "student": {"id": 1, "login": "testuser", "firstName": "Test", "lastName": "User"}, "type": "INSTRUCTOR_REGISTRATION"}]
                }
                """);

        initializeType(Exam.class, """
                {"id": 1, "title": "Test Exam", "exerciseGroups": [{"id": 1, "title": "Test Group", "exercises": []}], "studentExams": [{"id": 1, "submitted": false}]}
                """);

        initializeType(Post.class, """
                {"id": 1, "content": "Test post", "plagiarismCase": {"id": 1}}
                """);

        log.debug("Successfully initialized Jackson deserializers");
    }

    /**
     * Forces Jackson to build and cache the complete deserializer chain for the given type.
     * Uses both constructType (to resolve the type hierarchy) and readValue (to trigger
     * full deserializer resolution including nested property deserializers).
     */
    private void initializeType(Class<?> type, String sampleJson) {
        try {
            // Step 1: Force type resolution through the type factory
            JavaType javaType = objectMapper.getTypeFactory().constructType(type);

            // Step 2: Create a reader which forces deserializer resolution for the type
            objectMapper.readerFor(javaType);

            // Step 3: Perform an actual deserialization to ensure all nested deserializers
            // (including property-level ones) are fully resolved and cached
            objectMapper.readValue(sampleJson, type);
        }
        catch (Exception e) {
            // Log but don't fail - the key goal is deserializer cache population,
            // which happens during type/reader construction even if data parsing fails
            log.warn("Pre-initialization for {} completed with warning: {}", type.getSimpleName(), e.getMessage());
        }
    }
}
