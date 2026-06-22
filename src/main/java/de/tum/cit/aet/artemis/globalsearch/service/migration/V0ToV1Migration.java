package de.tum.cit.aet.artemis.globalsearch.service.migration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExerciseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.ExerciseSearchableEntityLoadService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateUuidUtil;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.WeaviateObject;

/**
 * Migrates from schema v0 (single {@code Exercises} collection) to v1 (unified {@code SearchableEntities} collection with
 * trigram tokenization).
 * <p>
 * The legacy {@code Exercises} collection is used only to enumerate which exercises were searchable under v0; its stored
 * properties and vectors are never reused. For each of those exercises that is not already present in
 * {@code SearchableEntities}, the exercise is loaded fresh from the database (the source of truth) and indexed via
 * {@link ExerciseSearchableEntityDTO#fromExercise}, so the migrated data is current rather than a stale v0 snapshot.
 * Exercises already present are left untouched. This is a best-effort skip: it avoids needlessly re-vectorizing
 * already-indexed exercises and usually avoids overwriting a newer version the live indexing path wrote. It is not a hard
 * concurrency guarantee — the batch insert upserts, so a live write that lands in the brief window between the existence
 * check and the batch insert could still be overwritten (with current database data, since the migration also reads from
 * the database); this is bounded to the one-time run and self-heals on the exercise's next edit. Exercises that no longer
 * exist in the database are skipped, so deleted exercises are not re-created. Each page is written with a single gRPC
 * batch insert that re-vectorizes through the target collection's
 * configured vectorizer. The legacy collection is deleted only after a fully successful run, and re-runs are idempotent
 * (the target UUIDs are deterministic and already-present exercises are skipped).
 * <p>
 * If the legacy collection does not exist, the migration is a no-op.
 */
public class V0ToV1Migration implements WeaviateMigration {

    private static final Logger log = LoggerFactory.getLogger(V0ToV1Migration.class);

    /**
     * The collection name used in schema v0 (the {@code develop} branch before the unified-search PR).
     */
    public static final String LEGACY_EXERCISES_COLLECTION = "Exercises";

    /**
     * Page and batch size. Each page is written in one gRPC batch insert that re-embeds the objects through the target
     * collection's configured vectorizer, so the size is kept moderate to keep a batch's total embedding time within the
     * client's 120s gRPC insert timeout even when the embedding backend is under load.
     */
    private static final int PAGE_SIZE = 50;

    private final ExerciseSearchableEntityLoadService exerciseLoadService;

    public V0ToV1Migration(ExerciseSearchableEntityLoadService exerciseLoadService) {
        this.exerciseLoadService = exerciseLoadService;
    }

    @Override
    public int targetVersion() {
        return 1;
    }

    @Override
    public String description() {
        return "Migrate exercises from legacy 'Exercises' collection to unified 'SearchableEntities' collection";
    }

    @Override
    public void migrate(WeaviateClient client, String collectionPrefix) throws IOException {
        String oldName = collectionPrefix + LEGACY_EXERCISES_COLLECTION;
        String newName = collectionPrefix + SearchableEntitySchema.COLLECTION_NAME;

        if (!client.collections.exists(oldName)) {
            log.info("V0→V1: Legacy '{}' collection not found, nothing to migrate", oldName);
            return;
        }

        log.info("V0→V1: Migrating exercises from '{}' into '{}' using current database data...", oldName, newName);

        var oldCollection = client.collections.use(oldName);
        var newCollection = client.collections.use(newName);

        int migrated = 0;
        int alreadyPresent = 0;
        int notInDatabase = 0;
        int skipped = 0;
        int failed = 0;

        // Cursor-based pagination over the legacy collection, which is used only to enumerate the v0-searchable exercise
        // ids (its stored properties/vectors are never reused). For each page we skip ids already present in the target
        // (a best-effort skip that avoids re-vectorizing already-indexed exercises and usually avoids overwriting a newer
        // live-indexed version; the batch insert upserts, so a write in the brief check-to-insert window can still be
        // overwritten with current database data), load the remaining exercises
        // fresh from the database, and write them with a single gRPC batch insert that re-vectorizes through the target
        // collection's configured vectorizer. The gRPC batch path uses the client's 120s insert timeout rather than the
        // 30s the original per-object REST upserts hit, and on a single node with back-to-back batches only the first
        // batch pays the embedding model's cold-start. Nothing is deleted from the legacy collection until the whole
        // migration succeeds; a failure leaves it intact for the next attempt, and re-runs are idempotent.
        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            final String afterCursor = cursor;
            var page = oldCollection.query.fetchObjects(builder -> {
                builder.limit(PAGE_SIZE);
                if (afterCursor != null) {
                    builder.after(afterCursor);
                }
                return builder;
            });

            var objects = page.objects();
            if (objects.isEmpty()) {
                break;
            }
            cursor = objects.getLast().uuid();

            // Collect the exercise ids from this page that are not already indexed in the target collection.
            List<Long> missingExerciseIds = new ArrayList<>();
            for (WeaviateObject<Map<String, Object>> obj : objects) {
                Map<String, Object> oldProperties = obj.properties();
                Object rawId = oldProperties != null ? oldProperties.get("exercise_id") : null;
                if (!(rawId instanceof Number exerciseIdNumber)) {
                    log.debug("V0→V1: Skipping legacy object {} with missing or non-numeric exercise_id: {}", obj.uuid(), rawId);
                    skipped++;
                    continue;
                }
                long exerciseId = exerciseIdNumber.longValue();
                String uuid = WeaviateUuidUtil.deterministicUuid(SearchableEntitySchema.TypeValues.EXERCISE, exerciseId);
                if (newCollection.data.exists(uuid)) {
                    alreadyPresent++;
                    continue;
                }
                missingExerciseIds.add(exerciseId);
            }

            if (!missingExerciseIds.isEmpty()) {
                // Load the missing exercises from the database (deleted ones are not returned) and batch-insert them.
                List<ExerciseSearchableEntityDTO> dtos = exerciseLoadService.loadExerciseDtos(missingExerciseIds);
                notInDatabase += missingExerciseIds.size() - dtos.size();

                List<WeaviateObject<Map<String, Object>>> batch = new ArrayList<>();
                for (ExerciseSearchableEntityDTO dto : dtos) {
                    String uuid = WeaviateUuidUtil.deterministicUuid(SearchableEntitySchema.TypeValues.EXERCISE, dto.exerciseId());
                    Map<String, Object> properties = dto.toPropertyMap();
                    batch.add(WeaviateObject.of(objectBuilder -> objectBuilder.uuid(uuid).properties(properties)));
                }

                if (!batch.isEmpty()) {
                    var response = newCollection.data.insertMany(batch);
                    List<String> errors = response.errors();
                    if (!errors.isEmpty()) {
                        errors.forEach(error -> log.warn("V0→V1: Batch insert error: {}", error));
                        failed += errors.size();
                        migrated += batch.size() - errors.size();
                    }
                    else {
                        migrated += batch.size();
                    }
                }
            }

            hasMore = objects.size() == PAGE_SIZE;
        }

        if (skipped > 0 || notInDatabase > 0) {
            // Surfaced at WARN so a lossy run is visible: skipped = malformed legacy objects, notInDatabase = ids no longer in the database.
            log.warn("V0→V1: Migration complete — migrated: {}, alreadyPresent: {}, notInDatabase: {}, skipped(malformed): {}, failed: {}", migrated, alreadyPresent, notInDatabase,
                    skipped, failed);
        }
        else {
            log.info("V0→V1: Migration complete — migrated: {}, alreadyPresent: {}, failed: {}", migrated, alreadyPresent, failed);
        }

        if (failed > 0) {
            throw new IOException("V0→V1: Migration failed for " + failed + " exercises. Aborting cleanup to prevent data loss.");
        }

        // Clean up the legacy collection only after a fully successful migration.
        client.collections.delete(oldName);
        log.info("V0→V1: Deleted legacy collection '{}'", oldName);
    }
}
