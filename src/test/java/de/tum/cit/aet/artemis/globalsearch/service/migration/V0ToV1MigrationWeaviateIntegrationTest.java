package de.tum.cit.aet.artemis.globalsearch.service.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateMigrationStartupService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateUuidUtil;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.VectorConfig;
import io.weaviate.client6.v1.api.collections.WeaviateObject;

/**
 * Integration test for {@link V0ToV1Migration#migrate} against a real Weaviate Testcontainer.
 * <p>
 * The test drives the migration with a dedicated, throwaway collection prefix and creates/seeds the legacy and target
 * collections itself, so it never touches the shared collections that other tests use. It verifies the migration logic
 * end to end: cursor pagination, property transformation, the gRPC batch insert, deletion of the legacy collection only
 * after a fully successful run, that orphan objects without an {@code exercise_id} are skipped, and — most importantly —
 * that the batch insert <b>overwrites</b> an object that already exists under the same deterministic UUID rather than
 * failing (the case that arises for entities the live indexing path has already written, and for idempotent re-runs).
 * <p>
 * Weaviate indexing is asynchronous, so every read is wrapped in an {@link org.awaitility.Awaitility} poll: the test
 * waits until the seeded legacy objects are actually queryable before running the migration (otherwise the migration
 * could read a partially-indexed collection), and waits for the migrated objects to appear afterwards.
 * <p>
 * The Testcontainer runs with {@code vectorizer-module=none}, so embedding via the {@code text2vec-openai} backend is not
 * exercised here (that is verified on staging against {@code logos}); objects are stored without a vector, which is
 * sufficient to assert the migration's data movement and upsert semantics.
 */
class V0ToV1MigrationWeaviateIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired(required = false)
    private WeaviateClient weaviateClient;

    @Autowired
    private ApplicationContext applicationContext;

    /** Dedicated prefix so these collections never collide with the shared ones used by other tests. */
    private static final String PREFIX = "MigTest_";

    private static final String OLD_COLLECTION = PREFIX + V0ToV1Migration.LEGACY_EXERCISES_COLLECTION;

    private static final String NEW_COLLECTION = PREFIX + SearchableEntitySchema.COLLECTION_NAME;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(weaviateClient != null, "Weaviate container not available");
        dropCollections();
        // Target (v1) collection the migration writes into; would normally be created by WeaviateService at startup.
        weaviateClient.collections.create(NEW_COLLECTION, collection -> {
            collection.vectorConfig(VectorConfig.selfProvided());
            collection.properties(Property.text(SearchableEntitySchema.Properties.TYPE));
            collection.properties(Property.integer(SearchableEntitySchema.Properties.ENTITY_ID));
            collection.properties(Property.text(SearchableEntitySchema.Properties.DESCRIPTION));
            collection.properties(Property.text(SearchableEntitySchema.Properties.TITLE));
            return collection;
        });
        // Legacy (v0) collection with the subset of properties the migration reads.
        weaviateClient.collections.create(OLD_COLLECTION, collection -> {
            collection.vectorConfig(VectorConfig.selfProvided());
            collection.properties(Property.integer("exercise_id"));
            collection.properties(Property.text("problem_statement"));
            collection.properties(Property.text(SearchableEntitySchema.Properties.TITLE));
            return collection;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (weaviateClient != null) {
            dropCollections();
        }
    }

    private void dropCollections() throws Exception {
        if (weaviateClient.collections.exists(OLD_COLLECTION)) {
            weaviateClient.collections.delete(OLD_COLLECTION);
        }
        if (weaviateClient.collections.exists(NEW_COLLECTION)) {
            weaviateClient.collections.delete(NEW_COLLECTION);
        }
    }

    @Test
    void migratesExercisesTransformsPropertiesAndDropsLegacyCollection() throws Exception {
        seedLegacyExercise(101L, "Implement a stack", "Stack Exercise");
        seedLegacyExercise(102L, "Implement a queue", "Queue Exercise");
        awaitLegacyObjectCount(2);

        new V0ToV1Migration().migrate(weaviateClient, PREFIX);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(weaviateClient.collections.exists(OLD_COLLECTION)).as("legacy collection dropped after success").isFalse();
            assertMigrated(101L, "Implement a stack", "Stack Exercise");
            assertMigrated(102L, "Implement a queue", "Queue Exercise");
        });
    }

    @Test
    void overwritesObjectThatAlreadyExistsInTargetInsteadOfFailing() throws Exception {
        // Simulate an exercise the live indexing path already wrote into the target under its deterministic UUID.
        String uuid = WeaviateUuidUtil.deterministicUuid(SearchableEntitySchema.TypeValues.EXERCISE, 200L);
        Map<String, Object> existing = targetProperties(200L, "stale description", "Stale Title");
        weaviateClient.collections.use(NEW_COLLECTION).data.insert(existing, builder -> builder.uuid(uuid));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(weaviateClient.collections.use(NEW_COLLECTION).data.exists(uuid)).isTrue());

        seedLegacyExercise(200L, "fresh description", "Fresh Title");
        awaitLegacyObjectCount(1);

        assertThatCode(() -> new V0ToV1Migration().migrate(weaviateClient, PREFIX)).doesNotThrowAnyException();

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(weaviateClient.collections.exists(OLD_COLLECTION)).isFalse();
            // The batch insert overwrote the pre-existing object rather than erroring on the UUID collision.
            assertMigrated(200L, "fresh description", "Fresh Title");
        });
    }

    @Test
    void skipsLegacyObjectsWithoutExerciseId() throws Exception {
        seedLegacyExercise(300L, "valid statement", "Valid Title");
        Map<String, Object> orphan = new HashMap<>();
        orphan.put("problem_statement", "orphan without id");
        orphan.put(SearchableEntitySchema.Properties.TITLE, "Orphan");
        weaviateClient.collections.use(OLD_COLLECTION).data.insert(orphan);
        awaitLegacyObjectCount(2);

        new V0ToV1Migration().migrate(weaviateClient, PREFIX);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(weaviateClient.collections.exists(OLD_COLLECTION)).isFalse();
            assertMigrated(300L, "valid statement", "Valid Title");
            assertThat(countMigratedExercises()).as("only the object with an exercise_id was migrated").isEqualTo(1);
        });
    }

    @Test
    void migrationStartupServiceIsRegisteredOnSchedulingNode() {
        // The migration fires from WeaviateMigrationStartupService's @PostConstruct, which runs when DeferredEagerBeanInitializer
        // force-instantiates every lazy singleton after startup. Guard that the bean is actually registered (its @Conditional and
        // scheduling-profile gates are satisfied), so that trigger path exists and the migration cannot silently never run.
        assertThat(applicationContext.getBeanNamesForType(WeaviateMigrationStartupService.class)).as("scheduling node registers the migration startup service").isNotEmpty();
    }

    @Test
    void isNoOpWhenLegacyCollectionIsAbsent() throws Exception {
        weaviateClient.collections.delete(OLD_COLLECTION);
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(weaviateClient.collections.exists(OLD_COLLECTION)).isFalse());

        assertThatCode(() -> new V0ToV1Migration().migrate(weaviateClient, PREFIX)).doesNotThrowAnyException();
        assertThat(countMigratedExercises()).isZero();
    }

    private void seedLegacyExercise(long exerciseId, String problemStatement, String title) throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put("exercise_id", exerciseId);
        properties.put("problem_statement", problemStatement);
        properties.put(SearchableEntitySchema.Properties.TITLE, title);
        weaviateClient.collections.use(OLD_COLLECTION).data.insert(properties);
    }

    private Map<String, Object> targetProperties(long entityId, String description, String title) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.EXERCISE);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, entityId);
        properties.put(SearchableEntitySchema.Properties.DESCRIPTION, description);
        properties.put(SearchableEntitySchema.Properties.TITLE, title);
        return properties;
    }

    /** Waits until the legacy collection actually returns the expected number of objects, so the migration reads a fully-indexed source. */
    private void awaitLegacyObjectCount(long expected) {
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(fetchAll(OLD_COLLECTION)).hasSize((int) expected));
    }

    private void assertMigrated(long entityId, String expectedDescription, String expectedTitle) {
        var match = fetchAll(NEW_COLLECTION).stream().filter(object -> object.properties().get(SearchableEntitySchema.Properties.ENTITY_ID) != null
                && ((Number) object.properties().get(SearchableEntitySchema.Properties.ENTITY_ID)).longValue() == entityId).findFirst();
        assertThat(match).as("migrated exercise %d present in target", entityId).isPresent();
        Map<String, Object> properties = match.get().properties();
        assertThat(properties.get(SearchableEntitySchema.Properties.TYPE)).isEqualTo(SearchableEntitySchema.TypeValues.EXERCISE);
        assertThat(properties.get(SearchableEntitySchema.Properties.DESCRIPTION)).isEqualTo(expectedDescription);
        assertThat(properties.get(SearchableEntitySchema.Properties.TITLE)).isEqualTo(expectedTitle);
    }

    private long countMigratedExercises() {
        return fetchAll(NEW_COLLECTION).stream()
                .filter(object -> SearchableEntitySchema.TypeValues.EXERCISE.equals(object.properties().get(SearchableEntitySchema.Properties.TYPE))).count();
    }

    private List<WeaviateObject<Map<String, Object>>> fetchAll(String collectionName) {
        return weaviateClient.collections.use(collectionName).query.fetchObjects(builder -> builder.limit(1000)).objects();
    }
}
