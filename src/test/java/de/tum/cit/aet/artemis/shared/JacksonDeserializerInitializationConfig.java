package de.tum.cit.aet.artemis.shared;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.event.EventListener;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.impl.FailingDeserializer;
import com.fasterxml.jackson.databind.type.TypeFactory;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Reaction;
import de.tum.cit.aet.artemis.communication.dto.AnswerPostResponseDTO;
import de.tum.cit.aet.artemis.communication.dto.PostResponseDTO;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;

/**
 * Test configuration that eagerly initialises Jackson deserializers for the entity types
 * exercised by integration tests, so that no test ever drives the first concurrent
 * construction of a deserializer through Jackson's cyclic-reference window.
 *
 * <h2>The race we are closing</h2>
 *
 * Jackson's {@code DeserializerCache} (see {@code _createAndCache2} in
 * {@code com.fasterxml.jackson.databind.deser.DeserializerCache}) builds a bean
 * deserializer in three phases under a single {@code ReentrantLock}:
 *
 * <ol>
 * <li>Construct the {@code BeanDeserializer} — every property's value deserializer
 * is initially the placeholder {@code FailingDeserializer} (Jackson uses it to
 * break cycles and to detect un-resolved properties).</li>
 * <li>Put the deserializer in {@code _incompleteDeserializers} so that recursive
 * resolution of cyclic references can find the in-progress instance.</li>
 * <li>Call {@code resolve(ctxt)} which walks the property table and replaces every
 * {@code FailingDeserializer} with the real per-property deserializer. After this
 * returns, the entry moves to the publicly visible {@code _cachedDeserializers}.</li>
 * </ol>
 *
 * The lock keeps unrelated callers safe. The hole is in step&nbsp;2: if {@code resolve}
 * of bean A walks into bean B, and bean B's {@code resolve} walks back into bean A
 * (a cycle), the cyclic lookup returns A's partially-built deserializer from
 * {@code _incompleteDeserializers}. B's resolve copies that partial reference onto its
 * own property. Later, when B is asked to deserialize, it dereferences the partial A,
 * hits the {@code FailingDeserializer} on one of A's properties, and throws
 * {@code "No _valueDeserializer assigned"}.
 *
 * <p>
 * The failure modes we have observed all have this shape — the failing chain
 * always terminates at a primitive-typed property of {@code User} (typically
 * {@code User["id"]}), reached through a join entity:
 * </p>
 *
 * <ul>
 * <li>{@code Post.reactions → Reaction.user → User["id"]}</li>
 * <li>{@code TutorialGroup.registrations → TutorialGroupRegistration.student → User["id"]}</li>
 * <li>Organization indexing (same shape, different parent)</li>
 * </ul>
 *
 * <h2>How priming closes the race</h2>
 *
 * If we walk every entity type through {@code findRootValueDeserializer} on a single
 * thread before any test runs, each type is constructed-and-resolved while no
 * concurrent build is in flight. Cyclic references encountered during {@code resolve}
 * are still handled via {@code _incompleteDeserializers}, but the resulting partial
 * reference is replaced by the real, fully-resolved instance the moment the outer
 * resolve completes — and because we do this single-threaded, no other thread ever
 * captures a partial reference. After this method returns, {@code _cachedDeserializers}
 * holds fully-resolved bean deserializers for every type we prime, and every later
 * concurrent first-use is served from that cache without re-entering the construction
 * path.
 *
 * <h2>What changed versus the previous implementation</h2>
 *
 * <ol>
 * <li><b>{@code ObjectMapper#canDeserialize} replaced with
 * {@link DeserializationContext#findRootValueDeserializer}.</b>
 * {@code canDeserialize} was deprecated in Jackson 2.18 and removed in 3.x. More
 * importantly, it <i>swallows</i> construction errors and silently returns false,
 * leaving the cache empty — which produces a no-op prime whose absence we only
 * discover later, at test time, as the race we set out to prevent.
 * {@code findRootValueDeserializer} surfaces the same error path as a real
 * deserialization and throws on failure.</li>
 * <li><b>{@link ApplicationReadyEvent} instead of {@code @PostConstruct}.</b>
 * {@code @PostConstruct} fires during bean initialisation. Later bean
 * post-processors and Jackson customisers can still mutate the {@code ObjectMapper}
 * after our {@code @PostConstruct} runs, invalidating priming. The
 * {@code ApplicationReadyEvent} fires after the entire context is published and
 * guarantees the {@code ObjectMapper} is in its final state.</li>
 * <li><b>Join entities listed explicitly.</b> Even though resolving a parent
 * (e.g. {@code TutorialGroup}) does walk into the element type of its
 * {@code Set<>} properties, doing so re-enters the cyclic-reference window for the
 * join entity. Priming the join entity ({@code TutorialGroupRegistration},
 * {@code Reaction}) as a root instead caches it without any sibling resolve in
 * flight.</li>
 * <li><b>{@code Set<T>} variants in addition to {@code List<T>}.</b> Jackson caches
 * the {@code CollectionDeserializer} keyed by the parameterised collection type;
 * a {@code List<User>} entry does not satisfy a {@code Set<User>} lookup. The
 * failing chains all go through {@code HashSet} (e.g. {@code Post.reactions}),
 * so we explicitly prime both.</li>
 * <li><b>Post-prime assertion.</b> After priming each entity we walk its
 * {@link BeanDeserializerBase} property table and refuse to start the context if
 * any property still has a {@code FailingDeserializer} value deserializer. This is
 * the precise condition that produces the runtime error; failing here turns a
 * flaky integration-test failure into a deterministic context-startup failure.</li>
 * <li><b>Loud failures and INFO-level logging.</b> The previous implementation logged
 * at {@code debug}, so no test log ever told us whether the prime ran — turning
 * silent prime regressions into long debug sessions.</li>
 * </ol>
 */
@TestConfiguration
public class JacksonDeserializerInitializationConfig {

    private static final Logger log = LoggerFactory.getLogger(JacksonDeserializerInitializationConfig.class);

    /**
     * Ordering matters: leaf-most types come first so that when a container type is
     * primed, every cyclic lookup it issues during {@code resolve()} finds a fully
     * resolved instance in {@code _cachedDeserializers} and never has to fall back to
     * {@code _incompleteDeserializers}.
     */
    private static final List<Class<?>> ENTITY_TYPES = List.of(
            // The leaf every failing chain terminates on. Must be first.
            User.class,
            // Top-level entities deserialized by integration tests.
            Organization.class, Course.class, Exam.class,
            // Communication join entity and parents:
            // Post.reactions -> Reaction.user -> User["id"]
            Reaction.class, Post.class, AnswerPost.class,
            // Tutorial-group join entity and parent:
            // TutorialGroup.registrations -> TutorialGroupRegistration.student -> User["id"]
            TutorialGroupRegistration.class, TutorialGroup.class);

    private final ObjectMapper objectMapper;

    public JacksonDeserializerInitializationConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @EventListener
    void primeOnApplicationReady(ApplicationReadyEvent event) {
        prime();
    }

    private void prime() {
        TypeFactory tf = objectMapper.getTypeFactory();
        DefaultDeserializationContext blueprint = (DefaultDeserializationContext) objectMapper.getDeserializationContext();
        DeserializationContext ctxt = blueprint.createInstance(objectMapper.getDeserializationConfig(), null, objectMapper.getInjectableValues());

        log.info("Priming Jackson deserializers for {} entity types", ENTITY_TYPES.size());
        for (Class<?> entityType : ENTITY_TYPES) {
            JsonDeserializer<?> bareDeser = forceConstruct(ctxt, tf.constructType(entityType), entityType.getSimpleName());
            forceConstruct(ctxt, tf.constructCollectionType(Set.class, entityType), "Set<" + entityType.getSimpleName() + ">");
            forceConstruct(ctxt, tf.constructCollectionType(List.class, entityType), "List<" + entityType.getSimpleName() + ">");
            assertNoFailingPlaceholders(bareDeser, entityType);
        }
        log.info("Jackson deserializer prime complete: {} bean types + {} collection wrappers", ENTITY_TYPES.size(), ENTITY_TYPES.size() * 2);

        exerciseFailureChains(tf);
    }

    /**
     * Actively deserialize the synthetic JSON shapes that match the previously failing chains
     * documented above, both for the cyclic entity types (which priming must have resolved) and
     * for the new cycle-free response DTOs (which must succeed by construction).
     * <p>
     * Surfaces any regression as a deterministic context-startup failure instead of an
     * intermittent integration-test flake whose stack trace points at a deserializer placeholder
     * far removed from the actual cyclic-reference root cause. The probe additionally exercises
     * {@code BeanDeserializer.deserialize} itself — {@code findRootValueDeserializer} alone
     * only constructs the deserializer; a {@code FailingDeserializer} can still be hiding in a
     * contextual variant that priming never touched.
     *
     * @param tf the Jackson {@link TypeFactory} used to build parameterised collection types
     */
    private void exerciseFailureChains(TypeFactory tf) {
        // Entity chains — these used to trigger the race. After priming above the deserializer
        // must be fully resolved, so readValue is expected to succeed deterministically.
        readValueOrThrow("[{\"id\":1,\"reactions\":[{\"id\":1,\"user\":{\"id\":1}}]}]", tf.constructCollectionType(List.class, Post.class), "List<Post> with reaction-user chain");
        readValueOrThrow("[{\"id\":1,\"reactions\":[{\"id\":1,\"user\":{\"id\":1}}]}]", tf.constructCollectionType(List.class, AnswerPost.class),
                "List<AnswerPost> with reaction-user chain");
        readValueOrThrow("{\"id\":1,\"registrations\":[{\"id\":1,\"student\":{\"id\":1}}]}", tf.constructType(TutorialGroup.class),
                "TutorialGroup with registrations-student chain");

        // DTO chains — must succeed by construction since the records carry no cyclic relations.
        // A failure here means someone added a back-reference to a response DTO and re-opened the
        // cycle this refactor closed.
        readValueOrThrow("[{\"id\":1,\"reactions\":[{\"id\":1,\"user\":{\"id\":1}}]}]", tf.constructCollectionType(List.class, PostResponseDTO.class),
                "List<PostResponseDTO> with reaction-user chain");
        readValueOrThrow("[{\"id\":1,\"reactions\":[{\"id\":1,\"user\":{\"id\":1}}]}]", tf.constructCollectionType(List.class, AnswerPostResponseDTO.class),
                "List<AnswerPostResponseDTO> with reaction-user chain");
        readValueOrThrow("{\"id\":1,\"reactions\":[{\"id\":1,\"user\":{\"id\":1}}],\"answers\":[{\"id\":2,\"reactions\":[{\"id\":3,\"user\":{\"id\":1}}]}]}",
                tf.constructType(PostResponseDTO.class), "PostResponseDTO with answers and nested reactions");

        log.info("Jackson failure-chain exercise complete: 6 chains deserialized without hitting the cyclic-reference race");
    }

    private void readValueOrThrow(String json, JavaType type, String label) {
        try {
            objectMapper.readValue(json, type);
        }
        catch (Exception e) {
            throw new IllegalStateException("Failure-chain probe failed for " + label + " — Jackson cyclic-reference race re-opened or the DTO shape regressed: " + e.getMessage(),
                    e);
        }
    }

    /**
     * Force Jackson to construct, resolve, and cache the deserializer for {@code type}.
     * Returns the cached instance so the caller can inspect its property table.
     */
    private JsonDeserializer<?> forceConstruct(DeserializationContext ctxt, JavaType type, String label) {
        try {
            JsonDeserializer<?> deser = ctxt.findRootValueDeserializer(type);
            if (deser == null) {
                throw new IllegalStateException("findRootValueDeserializer returned null for " + label);
            }
            if (deser instanceof FailingDeserializer) {
                throw new IllegalStateException("Got a FailingDeserializer placeholder as the root deserializer for " + label);
            }
            return deser;
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IllegalStateException("Failed to pre-initialize Jackson deserializer for " + label, e);
        }
    }

    /**
     * Walk the bean's property table and refuse to start if any property still has a
     * {@code FailingDeserializer} value deserializer. This is the exact condition that
     * produces the {@code "No _valueDeserializer assigned"} error at deserialization
     * time — surfacing it here turns a flaky test failure into a deterministic
     * context-startup failure that points directly at the unresolved property.
     */
    private void assertNoFailingPlaceholders(JsonDeserializer<?> deser, Class<?> entityType) {
        if (!(deser instanceof BeanDeserializerBase beanDeser)) {
            return;
        }
        Iterator<SettableBeanProperty> properties = beanDeser.properties();
        while (properties.hasNext()) {
            SettableBeanProperty property = properties.next();
            if (!property.hasValueDeserializer()) {
                throw new IllegalStateException("Jackson left a FailingDeserializer placeholder on " + entityType.getName() + "[\"" + property.getName()
                        + "\"] after priming — the cyclic-reference race was not closed. " + "Add the property's type to ENTITY_TYPES (prime it BEFORE "
                        + entityType.getSimpleName() + ") or check whether " + "a custom @JsonDeserialize on this property is preventing resolution.");
            }
        }
    }
}
