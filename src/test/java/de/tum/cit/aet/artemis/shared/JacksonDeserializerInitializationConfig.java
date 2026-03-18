package de.tum.cit.aet.artemis.shared;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;

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

        // Initialize User directly (most commonly nested entity, must be warmed first)
        warmUpDeserializer(User.class, """
                {
                    "id": 1,
                    "login": "testuser",
                    "firstName": "Test",
                    "lastName": "User",
                    "email": "test@test.com",
                    "activated": true
                }
                """);

        // Initialize Organization with nested User and Course
        warmUpDeserializer(Organization.class, """
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
                """);

        // Initialize Course with nested relationships (exercises, lectures, etc.)
        warmUpDeserializer(Course.class, """
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
                """);

        // Initialize Exam with nested relationships (exercise groups, student exams, etc.)
        warmUpDeserializer(Exam.class, """
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
                """);

        // Initialize TutorialGroup with nested registrations containing User references
        warmUpDeserializer(TutorialGroup.class, """
                {
                    "id": 1,
                    "title": "Test Group",
                    "teachingAssistant": {
                        "id": 1,
                        "login": "tutor1"
                    },
                    "registrations": [{
                        "id": 1,
                        "student": {
                            "id": 2,
                            "login": "student1"
                        }
                    }]
                }
                """);

        // Initialize Post with nested reactions containing User references
        warmUpDeserializer(Post.class, """
                {
                    "id": 1,
                    "content": "Test post",
                    "reactions": [{
                        "id": 1,
                        "emojiId": "rocket",
                        "user": {
                            "id": 1,
                            "login": "testuser"
                        }
                    }]
                }
                """);

        log.debug("Successfully initialized Jackson deserializers");
    }

    private <T> void warmUpDeserializer(Class<T> type, String sampleJson) {
        try {
            objectMapper.readValue(sampleJson, type);
        }
        catch (Exception e) {
            log.warn("Failed to pre-initialize {} deserializer: {}", type.getSimpleName(), e.getMessage());
        }
    }
}
