package de.tum.cit.aet.artemis.shared;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.event.EventListener;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.TypeFactory;

import de.tum.cit.aet.artemis.account.domain.Organization;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Reaction;
import de.tum.cit.aet.artemis.communication.dto.AnswerPostResponseDTO;
import de.tum.cit.aet.artemis.communication.dto.PostResponseDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseForDashboardDTO;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;

/**
 * Resolves the deserializers of the cyclic entity types before any test uses them.
 * <p>
 * Jackson caches a deserializer only once it is fully resolved. While it is still resolving one, a property whose type
 * is part of the same reference cycle can be handed a {@code FailingDeserializer} placeholder, and deserializing
 * through that placeholder fails with {@code "No _valueDeserializer assigned"} (issue #12798). Resolving the cyclic
 * types up front, one at a time and outside any concurrent test, closes that window.
 * <p>
 * PR #12791 removed most of the exposure by routing the affected endpoints through cycle-free response DTOs, but not
 * all of it: {@code CourseForDashboardDTO} still nests the {@code Course} entity, whose {@code exams} lead back into
 * {@code Exam}. That chain is what fails without this priming.
 * <p>
 * Ordering is load-bearing: a type is primed after everything it can reach, so that every cyclic lookup it issues
 * while resolving finds a finished deserializer in the cache instead of a placeholder.
 */
@TestConfiguration
public class JacksonDeserializerInitializationConfig {

    private static final Logger log = LoggerFactory.getLogger(JacksonDeserializerInitializationConfig.class);

    /**
     * Leaf-most types first. {@link User} is the leaf every failing chain terminates on, and {@link Exam} precedes
     * {@link Course} because {@code Course.exams} is the edge that reopened the cycle.
     */
    private static final List<Class<?>> ENTITY_TYPES = List.of(User.class, Organization.class, Exam.class, Course.class, Reaction.class, Post.class, AnswerPost.class,
            TutorialGroupRegistration.class, TutorialGroup.class);

    private final JsonMapper jsonMapper;

    public JacksonDeserializerInitializationConfig(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Primes once the context is published, so the mapper is in its final state.
     *
     * @param event the application-ready event, unused
     */
    @EventListener
    void primeOnApplicationReady(ApplicationReadyEvent event) {
        prime();
    }

    private void prime() {
        TypeFactory typeFactory = jsonMapper.getTypeFactory();
        log.info("Priming Jackson deserializers for {} entity types", ENTITY_TYPES.size());
        for (Class<?> entityType : ENTITY_TYPES) {
            String name = entityType.getSimpleName();
            // Jackson caches the collection variants separately from the bean, keyed on the parameterised type, so a
            // List<T> entry does not satisfy a Set<T> lookup and both have to be primed.
            resolve("{}", typeFactory.constructType(entityType), name);
            resolve("[]", typeFactory.constructCollectionType(Set.class, entityType), "Set<" + name + ">");
            resolve("[]", typeFactory.constructCollectionType(List.class, entityType), "List<" + name + ">");
        }
        exerciseFailureChains(typeFactory);
    }

    /**
     * Deserializes the shapes of the chains that have failed before, so an unresolved placeholder surfaces here as a
     * deterministic startup failure rather than as an intermittent integration-test flake whose stack trace points at
     * a {@code FailingDeserializer}.
     */
    private void exerciseFailureChains(TypeFactory typeFactory) {
        readValueOrThrow("[{\"id\":1,\"reactions\":[{\"id\":1,\"user\":{\"id\":1}}]}]", typeFactory.constructCollectionType(List.class, PostResponseDTO.class),
                "List<PostResponseDTO> with reaction-user chain");
        readValueOrThrow("[{\"id\":1,\"reactions\":[{\"id\":1,\"user\":{\"id\":1}}]}]", typeFactory.constructCollectionType(List.class, AnswerPostResponseDTO.class),
                "List<AnswerPostResponseDTO> with reaction-user chain");
        readValueOrThrow("{\"id\":1,\"reactions\":[{\"id\":1,\"user\":{\"id\":1}}],\"answers\":[{\"id\":2,\"reactions\":[{\"id\":3,\"user\":{\"id\":1}}]}]}",
                typeFactory.constructType(PostResponseDTO.class), "PostResponseDTO with answers and nested reactions");
        // the entity-typed chain PR #12791 did not remove: the dashboard DTO nests Course, which reaches Exam
        readValueOrThrow("{\"course\":{\"id\":1,\"exams\":[{\"id\":1,\"endDate\":\"2026-03-14T15:09:26.535+02:00\"}]}}", typeFactory.constructType(CourseForDashboardDTO.class),
                "CourseForDashboardDTO with a course-exams-endDate chain");

        log.info("Jackson chain probe complete: 4 chains deserialized");
    }

    private void readValueOrThrow(String json, JavaType type, String label) {
        try {
            jsonMapper.readValue(json, type);
        }
        catch (Exception e) {
            throw new IllegalStateException("Jackson chain probe failed for " + label + " — the cyclic-reference race is open again. Either a back-reference was added to a "
                    + "response DTO, or a type on this chain needs to be primed earlier in ENTITY_TYPES: " + e.getMessage(), e);
        }
    }

    private void resolve(String json, JavaType type, String label) {
        try {
            // reading an empty value is what makes Jackson build, resolve and cache the deserializer for this type
            jsonMapper.readerFor(type).readValue(json);
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to pre-resolve the Jackson deserializer for " + label, e);
        }
    }
}
