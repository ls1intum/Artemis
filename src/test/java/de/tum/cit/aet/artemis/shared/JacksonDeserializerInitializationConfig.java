package de.tum.cit.aet.artemis.shared;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Reaction;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
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
 * We initialize entity types at two levels:
 * <ul>
 * <li>Standalone types: forces full BeanDeserializer resolution for each entity independently</li>
 * <li>Nested structures: ensures contextual deserializers (from {@code @JsonIgnoreProperties},
 * {@code @JsonIncludeProperties}) are also cached</li>
 * <li>Collection types: ensures List/Set deserializers for common REST response types are cached</li>
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

        // Phase 1: Initialize standalone entity types to populate the base deserializer cache.
        // This ensures each entity's BeanDeserializer (and all its property deserializers)
        // are fully resolved before any concurrent test access.
        initializeType(User.class);
        initializeType(PlagiarismCase.class);
        initializeType(TutorialGroupRegistration.class);
        initializeType(Reaction.class);
        initializeType(AnswerPost.class);

        // Phase 2: Initialize complex root entities with nested relationships.
        // This also warms up contextual deserializers created by @JsonIgnoreProperties
        // and @JsonIncludeProperties annotations on relationship fields.
        initializeOrganization();
        initializeCourse();
        initializeExam();
        initializePost();
        initializeTutorialGroup();

        // Phase 3: Initialize collection types commonly returned by REST endpoints.
        // Tests often deserialize List<Entity> from HTTP responses; the collection
        // deserializer and its element deserializer must both be cached.
        initializeCollectionType(Post.class);
        initializeCollectionType(TutorialGroup.class);
        initializeCollectionType(Organization.class);

        log.debug("Successfully initialized Jackson deserializers");
    }

    /**
     * Initialize the deserializer for a single entity type by deserializing an empty JSON object.
     * This forces Jackson to create, resolve, and cache the BeanDeserializer and all its
     * property deserializers (including nested types).
     */
    private void initializeType(Class<?> type) {
        try {
            objectMapper.readValue("{}", type);
        }
        catch (Exception e) {
            log.warn("Failed to pre-initialize {} deserializer: {}", type.getSimpleName(), e.getMessage());
        }
    }

    /**
     * Initialize the deserializer for a List collection of the given element type.
     * This ensures the CollectionDeserializer and its element BeanDeserializer are cached.
     */
    private void initializeCollectionType(Class<?> elementType) {
        try {
            JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
            objectMapper.readValue("[]", listType);
        }
        catch (Exception e) {
            log.warn("Failed to pre-initialize List<{}> deserializer: {}", elementType.getSimpleName(), e.getMessage());
        }
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
                            "activated": true,
                            "deleted": false,
                            "langKey": "en",
                            "internal": true,
                            "memirisEnabled": true,
                            "bot": false
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

    private void initializePost() {
        try {
            String sampleJson = """
                    {
                        "id": 1,
                        "content": "Test post",
                        "reactions": [{
                            "id": 1,
                            "emojiId": "thumbsup",
                            "user": {
                                "id": 1,
                                "name": "Test User"
                            }
                        }],
                        "answers": [{
                            "id": 1,
                            "content": "Test answer",
                            "reactions": [{
                                "id": 2,
                                "emojiId": "smile",
                                "user": {
                                    "id": 2,
                                    "name": "Another User"
                                }
                            }]
                        }],
                        "plagiarismCase": {
                            "id": 1
                        }
                    }
                    """;
            objectMapper.readValue(sampleJson, Post.class);
        }
        catch (Exception e) {
            log.warn("Failed to pre-initialize Post deserializer: {}", e.getMessage());
        }
    }

    private void initializeTutorialGroup() {
        try {
            String sampleJson = """
                    {
                        "id": 1,
                        "title": "Test Tutorial Group",
                        "registrations": [{
                            "id": 1,
                            "student": {
                                "id": 1,
                                "login": "teststudent",
                                "firstName": "Test",
                                "lastName": "Student"
                            },
                            "type": "INSTRUCTOR_REGISTRATION"
                        }]
                    }
                    """;
            objectMapper.readValue(sampleJson, TutorialGroup.class);
        }
        catch (Exception e) {
            log.warn("Failed to pre-initialize TutorialGroup deserializer: {}", e.getMessage());
        }
    }

}
