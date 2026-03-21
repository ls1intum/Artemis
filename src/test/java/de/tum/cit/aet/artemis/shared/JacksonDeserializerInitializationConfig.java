package de.tum.cit.aet.artemis.shared;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Reaction;
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

        initializeDeserializer("User", User.class, """
                {"id": 1, "login": "testuser", "firstName": "Test", "lastName": "User", "email": "test@test.com", "activated": true, "imageUrl": null}
                """);

        initializeDeserializer("Organization", Organization.class, """
                {"id": 1, "name": "Test Organization", "shortName": "TO", "emailPattern": ".*@test.com",
                 "users": [{"id": 1, "login": "testuser", "firstName": "Test", "lastName": "User", "email": "test@test.com", "activated": true}],
                 "courses": [{"id": 1, "title": "Test Course", "shortName": "TC"}]}
                """);

        initializeDeserializer("Course", Course.class, """
                {"id": 1, "title": "Test Course", "shortName": "TC",
                 "exercises": [{"id": 1, "title": "Test Exercise", "type": "text"}],
                 "lectures": [{"id": 1, "title": "Test Lecture"}],
                 "organizations": [{"id": 1, "name": "Test Org"}]}
                """);

        initializeDeserializer("Exam", Exam.class, """
                {"id": 1, "title": "Test Exam",
                 "exerciseGroups": [{"id": 1, "title": "Test Group", "exercises": []}],
                 "studentExams": [{"id": 1, "submitted": false}]}
                """);

        initializeDeserializer("TutorialGroup", TutorialGroup.class, """
                {"id": 1, "title": "Test Group", "capacity": 10, "isOnline": false, "campus": "Test Campus", "language": "ENGLISH",
                 "registrations": [{"id": 1, "student": {"id": 1, "login": "testuser", "firstName": "Test", "lastName": "User"}, "type": "INSTRUCTOR_REGISTRATION"}],
                 "teachingAssistant": {"id": 2, "login": "tutor1", "firstName": "Tutor", "lastName": "One"}}
                """);

        initializeDeserializer("Reaction", Reaction.class, """
                {"id": 1, "emojiId": "smiley", "user": {"id": 1, "name": "Test User"}, "post": {"id": 1}}
                """);

        initializeDeserializer("Post", Post.class, """
                {"id": 1, "content": "Test",
                 "reactions": [{"id": 1, "emojiId": "smiley", "user": {"id": 1, "name": "Test User"}}],
                 "author": {"id": 1, "name": "Author", "imageUrl": null}}
                """);

        log.debug("Successfully initialized Jackson deserializers");
    }

    private void initializeDeserializer(String entityName, Class<?> type, String sampleJson) {
        try {
            objectMapper.readValue(sampleJson, type);
        }
        catch (Exception e) {
            log.warn("Failed to pre-initialize {} deserializer: {}", entityName, e.getMessage());
        }
    }
}
