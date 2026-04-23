package de.tum.cit.aet.artemis.shared;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.exam.domain.Exam;

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
 * We focus on key "root" entities that:
 * <ul>
 * <li>Have complex bidirectional relationships with {@code @JsonIgnoreProperties}</li>
 * <li>Are commonly returned in REST responses with nested data</li>
 * <li>Contain many nested entity types (initializing them also initializes their children)</li>
 * </ul>
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

        // Initialize Organization with nested User and Course
        initializeOrganization();

        // Initialize Course with nested relationships (exercises, lectures, etc.)
        initializeCourse();

        // Initialize Exam with nested relationships (exercise groups, student exams, etc.)
        initializeExam();

        log.debug("Successfully initialized Jackson deserializers");
    }

    private void initializeOrganization() {
        try {
            String sampleJson = """
                    {
                        "id": 1,
                        "name": "Test Organization",
                        "shortName": "TO",
                        "emailPattern": ".*@test.com",
                        "users": [{
                            "id": 1,
                            "login": "testuser",
                            "firstName": "Test",
                            "lastName": "User",
                            "email": "test@test.com",
                            "activated": true
                        }],
                        "courses": [{
                            "id": 1,
                            "title": "Test Course",
                            "shortName": "TC"
                        }]
                    }
                    """;
            objectMapper.readValue(sampleJson, Organization.class);
        }
        catch (Exception e) {
            log.warn("Failed to pre-initialize Organization deserializer: {}", e.getMessage());
        }
    }

    private void initializeCourse() {
        try {
            String sampleJson = """
                    {
                        "id": 1,
                        "title": "Test Course",
                        "shortName": "TC",
                        "exercises": [{
                            "id": 1,
                            "title": "Test Exercise",
                            "type": "text"
                        }],
                        "lectures": [{
                            "id": 1,
                            "title": "Test Lecture"
                        }],
                        "organizations": [{
                            "id": 1,
                            "name": "Test Org"
                        }]
                    }
                    """;
            objectMapper.readValue(sampleJson, Course.class);
        }
        catch (Exception e) {
            log.warn("Failed to pre-initialize Course deserializer: {}", e.getMessage());
        }
    }

    private void initializeExam() {
        try {
            String sampleJson = """
                    {
                        "id": 1,
                        "title": "Test Exam",
                        "exerciseGroups": [{
                            "id": 1,
                            "title": "Test Group",
                            "exercises": []
                        }],
                        "studentExams": [{
                            "id": 1,
                            "submitted": false
                        }]
                    }
                    """;
            objectMapper.readValue(sampleJson, Exam.class);
        }
        catch (Exception e) {
            log.warn("Failed to pre-initialize Exam deserializer: {}", e.getMessage());
        }
    }
}
