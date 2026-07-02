package de.tum.cit.aet.artemis.globalsearch.service.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.ExerciseSearchableEntityLoadService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateUuidUtil;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.VectorConfig;

/**
 * Integration test for {@link V0ToV1Migration#migrate} against a real Weaviate Testcontainer and a real database.
 * <p>
 * It creates an exercise in the database, records its id in a throwaway legacy {@code Exercises} collection, and runs the
 * migration into a throwaway target collection (both under a dedicated prefix so the shared collections are untouched).
 * It verifies the migration backfills the exercise from the <b>database</b> (not the legacy Weaviate snapshot), skips an
 * exercise already present in the target (never clobbering a concurrent live update), skips ids no longer in the database
 * (deleted exercises are not re-created), and drops the legacy collection only on success.
 * <p>
 * Weaviate indexing is asynchronous, so reads are wrapped in {@link org.awaitility.Awaitility} polls. The container runs
 * with {@code vectorizer-module=none}, so objects are stored without an embedding; that is sufficient to assert the
 * migration's data movement and skip semantics (embedding itself is validated on staging).
 */
@EnabledIf("isWeaviateEnabled")
class V0ToV1MigrationWeaviateIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "v0v1mig";

    @Autowired
    private WeaviateClient weaviateClient;

    @Autowired
    private ExerciseSearchableEntityLoadService exerciseLoadService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private static final String COLLECTION_PREFIX = "MigTest_";

    private static final String OLD_COLLECTION = COLLECTION_PREFIX + V0ToV1Migration.LEGACY_EXERCISES_COLLECTION;

    private static final String NEW_COLLECTION = COLLECTION_PREFIX + SearchableEntitySchema.COLLECTION_NAME;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private ProgrammingExercise exercise;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        dropCollections();
        // Throwaway target collection (the real one is created by WeaviateService at startup; auto-schema adds the DTO's
        // remaining properties on insert).
        weaviateClient.collections.create(NEW_COLLECTION, collection -> {
            collection.vectorConfig(VectorConfig.selfProvided());
            collection.properties(Property.text(SearchableEntitySchema.Properties.TYPE));
            collection.properties(Property.integer(SearchableEntitySchema.Properties.ENTITY_ID));
            collection.properties(Property.text(SearchableEntitySchema.Properties.TITLE));
            return collection;
        });
        // Throwaway legacy collection holding only the searchable exercise ids.
        weaviateClient.collections.create(OLD_COLLECTION, collection -> {
            collection.vectorConfig(VectorConfig.selfProvided());
            collection.properties(Property.integer("exercise_id"));
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
    void backfillsMissingExerciseFromDatabaseAndDropsLegacyCollection() throws Exception {
        seedLegacyExerciseId(exercise.getId());

        new V0ToV1Migration(exerciseLoadService).migrate(weaviateClient, COLLECTION_PREFIX);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Optional<Map<String, Object>> migrated = fetchTargetByEntityId(exercise.getId());
            assertThat(migrated).as("exercise backfilled into target").isPresent();
            assertThat(migrated.get().get(SearchableEntitySchema.Properties.TYPE)).isEqualTo(SearchableEntitySchema.TypeValues.EXERCISE);
            // The data comes from the database, not the legacy Weaviate snapshot.
            assertThat(migrated.get().get(SearchableEntitySchema.Properties.TITLE)).isEqualTo(exercise.getTitle());
        });
        assertThat(weaviateClient.collections.exists(OLD_COLLECTION)).as("legacy collection dropped after success").isFalse();
    }

    @Test
    void backfillsExamExerciseFromDatabase() throws Exception {
        // Covers the exam path of the eager fetch (exerciseGroup → exam → course); if it were not eagerly loaded,
        // fromExercise would fail and the exercise would be skipped, so isPresent() would fail here.
        ProgrammingExercise examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        seedLegacyExerciseId(examExercise.getId());

        new V0ToV1Migration(exerciseLoadService).migrate(weaviateClient, COLLECTION_PREFIX);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Optional<Map<String, Object>> migrated = fetchTargetByEntityId(examExercise.getId());
            assertThat(migrated).as("exam exercise backfilled from the database").isPresent();
            assertThat(migrated.get().get(SearchableEntitySchema.Properties.TITLE)).isEqualTo(examExercise.getTitle());
            assertThat(migrated.get().get(SearchableEntitySchema.Properties.IS_EXAM_EXERCISE)).isEqualTo(true);
        });
        assertThat(weaviateClient.collections.exists(OLD_COLLECTION)).isFalse();
    }

    @Test
    void skipsExerciseAlreadyPresentInTargetWithoutOverwriting() throws Exception {
        // Simulate a newer version the live indexing path already wrote into the target.
        String uuid = WeaviateUuidUtil.deterministicUuid(SearchableEntitySchema.TypeValues.EXERCISE, exercise.getId());
        Map<String, Object> live = new HashMap<>();
        live.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.EXERCISE);
        live.put(SearchableEntitySchema.Properties.ENTITY_ID, exercise.getId());
        live.put(SearchableEntitySchema.Properties.TITLE, "Live title written by the indexing path");
        weaviateClient.collections.use(NEW_COLLECTION).data.insert(live, builder -> builder.uuid(uuid));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(weaviateClient.collections.use(NEW_COLLECTION).data.exists(uuid)).isTrue());

        seedLegacyExerciseId(exercise.getId());

        new V0ToV1Migration(exerciseLoadService).migrate(weaviateClient, COLLECTION_PREFIX);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            Optional<Map<String, Object>> present = fetchTargetByEntityId(exercise.getId());
            assertThat(present).isPresent();
            // Not overwritten with the database title — the live version is preserved.
            assertThat(present.get().get(SearchableEntitySchema.Properties.TITLE)).isEqualTo("Live title written by the indexing path");
        });
        assertThat(weaviateClient.collections.exists(OLD_COLLECTION)).isFalse();
    }

    @Test
    void skipsLegacyIdNoLongerInDatabase() throws Exception {
        long deletedExerciseId = 999_999_999L;
        seedLegacyExerciseId(deletedExerciseId);

        new V0ToV1Migration(exerciseLoadService).migrate(weaviateClient, COLLECTION_PREFIX);

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(weaviateClient.collections.exists(OLD_COLLECTION)).isFalse());
        assertThat(fetchTargetByEntityId(deletedExerciseId)).as("deleted exercise is not re-created").isEmpty();
    }

    private void seedLegacyExerciseId(long exerciseId) throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put("exercise_id", exerciseId);
        weaviateClient.collections.use(OLD_COLLECTION).data.insert(properties);
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(weaviateClient.collections.use(OLD_COLLECTION).query.fetchObjects(builder -> builder.limit(100)).objects()).isNotEmpty());
    }

    private Optional<Map<String, Object>> fetchTargetByEntityId(long entityId) {
        return weaviateClient.collections.use(NEW_COLLECTION).query.fetchObjects(builder -> builder.limit(1000)).objects().stream().map(object -> object.properties())
                .filter(properties -> properties.get(SearchableEntitySchema.Properties.ENTITY_ID) != null
                        && ((Number) properties.get(SearchableEntitySchema.Properties.ENTITY_ID)).longValue() == entityId)
                .findFirst();
    }
}
