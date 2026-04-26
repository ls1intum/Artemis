package de.tum.cit.aet.artemis.shared;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Reaction;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
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
 * By eagerly resolving deserializers at startup, we ensure the deserializer cache is
 * properly populated before any tests run, eliminating the race condition.
 * <p>
 * We cover all entity types that have been observed to cause race condition failures
 * in CI, including their nested relationships.
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
        log.info("Eagerly initializing Jackson deserializers for entity types");

        // Force Jackson to build and cache BeanDeserializers for all entity types
        // that are commonly deserialized in integration tests. This prevents race
        // conditions when tests run in parallel and trigger first-time deserialization
        // of these types concurrently.
        List<Class<?>> typesToInitialize = List.of(User.class, Organization.class, Course.class, Exam.class, TutorialGroup.class, TutorialGroupRegistration.class, Post.class,
                Reaction.class, Exercise.class, QuizExercise.class);

        for (Class<?> type : typesToInitialize) {
            forceDeserializerCreation(type);
        }

        // Also perform actual deserialization to fully populate nested property deserializers
        initializeOrganization();
        initializeCourse();
        initializeExam();
        initializeTutorialGroup();
        initializePost();

        // Initialize collection type deserializers (List<T>, Set<T>) — these use different
        // cache entries than single-object deserializers and are commonly used by
        // RequestUtilService.getList() / getSet() in integration tests.
        for (Class<?> type : typesToInitialize) {
            forceCollectionDeserializerCreation(type);
        }

        // Initialize List deserialization with nested objects — this warms up the
        // contextualized deserializers (created by @JsonIgnoreProperties) that are
        // different from the single-object deserializers cached above.
        initializeTutorialGroupList();
        initializeOrganizationList();

        log.info("Successfully initialized Jackson deserializers for {} types", typesToInitialize.size());
    }

    /**
     * Forces Jackson to create and cache the BeanDeserializer for the given type.
     * This ensures that when tests later deserialize this type concurrently,
     * the deserializer is already fully initialized.
     */
    private void forceDeserializerCreation(Class<?> type) {
        try {
            // Force the type to be fully resolved by reading an empty object.
            // This creates and caches the BeanDeserializer and all its property deserializers.
            objectMapper.readValue("{}", type);
        }
        catch (Exception e) {
            // Expected for types that require specific fields; the important thing
            // is that the deserializer was created and cached during the attempt
            log.debug("Pre-initialization attempt for {} (expected): {}", type.getSimpleName(), e.getMessage());
        }
    }

    /**
     * Forces Jackson to create and cache collection deserializers (List and Set) for the given element type.
     * Integration tests frequently deserialize responses as List<T> or Set<T> via RequestUtilService,
     * which uses a different deserializer cache entry than single-object deserialization.
     */
    private void forceCollectionDeserializerCreation(Class<?> elementType) {
        try {
            JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
            objectMapper.readValue("[]", listType);
        }
        catch (Exception e) {
            log.debug("Pre-initialization of List<{}> (expected): {}", elementType.getSimpleName(), e.getMessage());
        }
        try {
            JavaType setType = objectMapper.getTypeFactory().constructCollectionType(java.util.Set.class, elementType);
            objectMapper.readValue("[]", setType);
        }
        catch (Exception e) {
            log.debug("Pre-initialization of Set<{}> (expected): {}", elementType.getSimpleName(), e.getMessage());
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

    private void initializeTutorialGroup() {
        try {
            String sampleJson = """
                    {
                        "id": 1,
                        "title": "Test Group",
                        "capacity": 10,
                        "isOnline": false,
                        "language": "ENGLISH",
                        "campus": "Test Campus",
                        "teachingAssistant": {
                            "id": 1,
                            "login": "tutor1",
                            "firstName": "Test",
                            "lastName": "Tutor"
                        },
                        "registrations": [{
                            "id": 1,
                            "student": {
                                "id": 2,
                                "login": "student1",
                                "firstName": "Test",
                                "lastName": "Student"
                            }
                        }]
                    }
                    """;
            objectMapper.readValue(sampleJson, TutorialGroup.class);
        }
        catch (Exception e) {
            log.warn("Failed to pre-initialize TutorialGroup deserializer: {}", e.getMessage());
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
                            "emojiId": "smile",
                            "user": {
                                "id": 1,
                                "login": "testuser",
                                "firstName": "Test",
                                "lastName": "User"
                            }
                        }]
                    }
                    """;
            objectMapper.readValue(sampleJson, Post.class);
        }
        catch (Exception e) {
            log.warn("Failed to pre-initialize Post deserializer: {}", e.getMessage());
        }
    }

    /**
     * Initializes List<TutorialGroup> deserialization with nested registrations and students.
     * This matches the exact deserialization path used by RequestUtilService.getList() in
     * TutorialGroupIntegrationTest, including contextualized deserializers created by
     * {@code @JsonIgnoreProperties} annotations on the relationship fields.
     */
    private void initializeTutorialGroupList() {
        try {
            String sampleJson = """
                    [{
                        "id": 1,
                        "title": "Test Group",
                        "capacity": 10,
                        "isOnline": false,
                        "teachingAssistant": {
                            "id": 1,
                            "login": "tutor1"
                        },
                        "registrations": [{
                            "id": 1,
                            "student": {
                                "id": 2,
                                "login": "student1",
                                "firstName": "Test",
                                "lastName": "Student"
                            }
                        }]
                    }]
                    """;
            JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, TutorialGroup.class);
            objectMapper.readValue(sampleJson, listType);
        }
        catch (Exception e) {
            log.warn("Failed to pre-initialize List<TutorialGroup> deserializer: {}", e.getMessage());
        }
    }

    /**
     * Initializes List<Organization> deserialization with nested users.
     * This matches the deserialization path used by Organization integration tests.
     */
    private void initializeOrganizationList() {
        try {
            String sampleJson = """
                    [{
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
                        }]
                    }]
                    """;
            JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, Organization.class);
            objectMapper.readValue(sampleJson, listType);
        }
        catch (Exception e) {
            log.warn("Failed to pre-initialize List<Organization> deserializer: {}", e.getMessage());
        }
    }
}
