package de.tum.cit.aet.artemis.globalsearch.service.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.WeaviateObject;

/**
 * Migrates from schema v0 (single {@code Exercises} collection) to v1 (unified
 * {@code SearchableEntities} collection).
 * <p>
 * Reads all exercise objects from the legacy {@code Exercises} collection, transforms
 * their properties to the v1 schema (renaming {@code exercise_id → entity_id},
 * {@code problem_statement → description}, adding {@code type = "exercise"}), upserts
 * them into {@code SearchableEntities}, and deletes the old collection.
 * <p>
 * If the legacy collection does not exist, the migration is a no-op.
 */
public class V0ToV1Migration implements WeaviateMigration {

    private static final Logger log = LoggerFactory.getLogger(V0ToV1Migration.class);

    /**
     * The collection name used in schema v0 (the {@code develop} branch before the
     * unified-search PR).
     */
    static final String LEGACY_EXERCISES_COLLECTION = "Exercises";

    private static final int PAGE_SIZE = 100;

    /**
     * Must match {@code SearchableEntityWeaviateService.WEAVIATE_UUID_NAMESPACE} so
     * that migrated objects receive the same deterministic UUID as a normal upsert.
     */
    private static final UUID WEAVIATE_UUID_NAMESPACE = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

    /**
     * Properties that kept the same name between the v0 {@code Exercises} and v1
     * {@code SearchableEntities} schemas and can be copied unchanged.
     */
    private static final String[] DIRECT_MAPPINGS = { SearchableEntitySchema.Properties.COURSE_ID, SearchableEntitySchema.Properties.TITLE,
            SearchableEntitySchema.Properties.SHORT_NAME, SearchableEntitySchema.Properties.RELEASE_DATE, SearchableEntitySchema.Properties.START_DATE,
            SearchableEntitySchema.Properties.DUE_DATE, SearchableEntitySchema.Properties.EXERCISE_TYPE, SearchableEntitySchema.Properties.DIFFICULTY,
            SearchableEntitySchema.Properties.MAX_POINTS, SearchableEntitySchema.Properties.IS_EXAM_EXERCISE, SearchableEntitySchema.Properties.EXAM_ID,
            SearchableEntitySchema.Properties.EXAM_VISIBLE_DATE, SearchableEntitySchema.Properties.EXAM_START_DATE, SearchableEntitySchema.Properties.EXAM_END_DATE,
            SearchableEntitySchema.Properties.TEST_EXAM, SearchableEntitySchema.Properties.PROGRAMMING_LANGUAGE, SearchableEntitySchema.Properties.PROJECT_TYPE,
            SearchableEntitySchema.Properties.DIAGRAM_TYPE, SearchableEntitySchema.Properties.QUIZ_MODE, SearchableEntitySchema.Properties.QUIZ_DURATION,
            SearchableEntitySchema.Properties.FILE_PATTERN, };

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
            log.info("V0→V1: Legacy '{}' collection not found, skipping data migration", oldName);
            return;
        }

        log.info("V0→V1: Migrating data from '{}' to '{}'...", oldName, newName);

        var oldCollection = client.collections.use(oldName);
        var newCollection = client.collections.use(newName);

        int migrated = 0;
        int skipped = 0;
        int failed = 0;

        // Cursor-based pagination: read all objects from the old collection in pages
        String cursor = null;
        boolean hasMore = true;

        while (hasMore) {
            final String afterCursor = cursor;
            var result = oldCollection.query.fetchObjects(b -> {
                b.limit(PAGE_SIZE);
                if (afterCursor != null) {
                    b.after(afterCursor);
                }
                return b;
            });

            var objects = result.objects();
            if (objects.isEmpty()) {
                break;
            }

            for (WeaviateObject<Map<String, Object>> obj : objects) {
                Object rawId = obj.properties().get("exercise_id");
                if (rawId == null) {
                    skipped++;
                    continue;
                }
                long entityId = ((Number) rawId).longValue();

                try {
                    Map<String, Object> newProps = transformProperties(obj.properties());
                    String uuid = deterministicUuid(SearchableEntitySchema.TypeValues.EXERCISE, entityId);

                    if (newCollection.data.exists(uuid)) {
                        newCollection.data.replace(uuid, r -> r.properties(newProps));
                    }
                    else {
                        newCollection.data.insert(newProps, o -> o.uuid(uuid));
                    }
                    migrated++;
                }
                catch (Exception e) {
                    log.warn("V0→V1: Failed to migrate exercise {}: {}", entityId, e.getMessage());
                    failed++;
                }
            }

            cursor = objects.getLast().uuid();
            hasMore = objects.size() == PAGE_SIZE;
        }

        log.info("V0→V1: Data migration complete — migrated: {}, skipped: {}, failed: {}", migrated, skipped, failed);

        // Clean up the legacy collection
        client.collections.delete(oldName);
        log.info("V0→V1: Deleted legacy collection '{}'", oldName);
    }

    /**
     * Transforms properties from the v0 {@code Exercises} schema to the v1
     * {@code SearchableEntities} schema.
     * <p>
     * Key changes:
     * <ul>
     * <li>{@code exercise_id} → {@code entity_id}</li>
     * <li>{@code problem_statement} → {@code description}</li>
     * <li>{@code type = "exercise"} added</li>
     * <li>{@code course_name} dropped (no longer stored in Weaviate)</li>
     * </ul>
     *
     * @param oldProps the property map from the v0 collection
     * @return the transformed property map for the v1 collection
     */
    static Map<String, Object> transformProperties(Map<String, Object> oldProps) {
        Map<String, Object> newProps = new HashMap<>();

        // Type discriminator (new in v1)
        newProps.put(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.EXERCISE);

        // Renamed properties
        newProps.put(SearchableEntitySchema.Properties.ENTITY_ID, oldProps.get("exercise_id"));
        newProps.put(SearchableEntitySchema.Properties.DESCRIPTION, oldProps.get("problem_statement"));

        // Directly mapped properties (same name in v0 and v1)
        for (String prop : DIRECT_MAPPINGS) {
            Object value = oldProps.get(prop);
            if (value != null) {
                newProps.put(prop, value);
            }
        }

        // Dropped: course_name (resolved from DB at query time in v1)

        // Remove null entries to keep Weaviate objects clean
        newProps.values().removeIf(Objects::isNull);

        return newProps;
    }

    /**
     * Derives a deterministic UUID v3 from {@code (type, entityId)}, matching the logic in
     * {@code SearchableEntityWeaviateService.deterministicUuid()} so migrated objects get the
     * same UUID as they would from a normal upsert.
     */
    private static String deterministicUuid(String type, Long entityId) {
        return UUID.nameUUIDFromBytes((WEAVIATE_UUID_NAMESPACE + ":" + type + ":" + entityId).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
