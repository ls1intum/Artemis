package de.tum.cit.aet.artemis.globalsearch.service.migration;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.WeaviateDateUtil;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateUuidUtil;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.WeaviateObject;

/**
 * Migrates from schema v0 (single {@code Exercises} collection) to v1 (unified
 * {@code SearchableEntities} collection with trigram tokenization).
 * <p>
 * Reads all exercise objects from the legacy {@code Exercises} collection, transforms their properties to the v1 schema
 * (renaming {@code exercise_id → entity_id}, {@code problem_statement → description}, adding {@code type = "exercise"}),
 * and upserts each page into the {@code SearchableEntities} collection that
 * {@link de.tum.cit.aet.artemis.globalsearch.service.WeaviateService#initializeCollections()} already created with the
 * correct trigram tokenization, using a single gRPC batch insert per page. Objects are inserted with properties only, so
 * Weaviate re-embeds them through the target collection's configured vectorizer; this keeps the migrated objects'
 * vectors consistent with the entities the live indexing path writes natively (a carried v0 vector would have been
 * computed from a slightly different set of property values). Once every page has been migrated, the old collection is
 * deleted.
 * <p>
 * If the legacy collection does not exist, the migration is a no-op.
 */
public class V0ToV1Migration implements WeaviateMigration {

    private static final Logger log = LoggerFactory.getLogger(V0ToV1Migration.class);

    /**
     * The collection name used in schema v0 (the {@code develop} branch before the
     * unified-search PR).
     */
    public static final String LEGACY_EXERCISES_COLLECTION = "Exercises";

    /**
     * Page and batch size. Each page is written in one gRPC batch insert that re-embeds the objects through the target
     * collection's configured vectorizer, so the size is kept moderate to keep a batch's total embedding time within the
     * client's 120s gRPC insert timeout even when the embedding backend is under load.
     */
    private static final int PAGE_SIZE = 50;

    /**
     * Matches ISO date-time strings that are missing the seconds component,
     * e.g. {@code 2026-04-24T20:25Z} or {@code 2026-04-24T20:25+02:00}.
     * Weaviate requires full RFC3339 with seconds ({@code 2026-04-24T20:25:00Z}).
     */
    private static final Pattern MISSING_SECONDS = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2})(Z|[+-])");

    /**
     * Date properties that may need RFC3339 normalization during migration.
     * Reuses the canonical set from {@link WeaviateDateUtil} so that newly added date properties
     * are automatically covered without maintaining a separate list.
     */
    private static final Set<String> DATE_PROPERTIES = WeaviateDateUtil.DATE_PROPERTY_KEYS;

    /**
     * Properties that kept the same name between the v0 {@code Exercises} and v1
     * {@code SearchableEntities} schemas and can be copied unchanged.
     */
    private static final String[] DIRECT_MAPPINGS = { SearchableEntitySchema.Properties.COURSE_ID, SearchableEntitySchema.Properties.TITLE,
            SearchableEntitySchema.Properties.SHORT_NAME, SearchableEntitySchema.Properties.RELEASE_DATE, SearchableEntitySchema.Properties.START_DATE,
            SearchableEntitySchema.Properties.DUE_DATE, SearchableEntitySchema.Properties.DIFFICULTY, SearchableEntitySchema.Properties.MAX_POINTS,
            SearchableEntitySchema.Properties.IS_EXAM_EXERCISE, SearchableEntitySchema.Properties.EXAM_ID, SearchableEntitySchema.Properties.EXAM_VISIBLE_DATE,
            SearchableEntitySchema.Properties.EXAM_START_DATE, SearchableEntitySchema.Properties.EXAM_END_DATE, SearchableEntitySchema.Properties.TEST_EXAM,
            SearchableEntitySchema.Properties.PROGRAMMING_LANGUAGE, SearchableEntitySchema.Properties.PROJECT_TYPE, SearchableEntitySchema.Properties.DIAGRAM_TYPE,
            SearchableEntitySchema.Properties.QUIZ_MODE, SearchableEntitySchema.Properties.QUIZ_DURATION, SearchableEntitySchema.Properties.FILE_PATTERN, };

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

        log.info("V0→V1: Migrating data from '{}' to '{}'...", oldName, newName);

        var oldCollection = client.collections.use(oldName);
        var newCollection = client.collections.use(newName);

        int migrated = 0;
        int skipped = 0;
        int failed = 0;

        // Cursor-based pagination over the legacy collection. Each page is transformed and written to the target
        // collection with a single gRPC batch insert (insertMany). Objects are inserted with properties only (no explicit
        // vector), so Weaviate re-embeds them through the target collection's configured vectorizer, keeping their vectors
        // consistent with the entities the live indexing path writes natively. The gRPC batch path uses the client's
        // insert timeout (120s) rather than the 30s the original per-object REST upserts hit, and because the migration
        // runs on a single node with back-to-back batches, only the first batch pays the embedding model's cold-start;
        // the model then stays warm for the remaining batches. Nothing is deleted from the legacy collection until the
        // whole migration succeeds, at which point it is dropped; a failure leaves the legacy data intact for the next
        // attempt, and re-runs are idempotent because the target UUIDs are deterministic.
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

            List<WeaviateObject<Map<String, Object>>> batch = new ArrayList<>();
            for (WeaviateObject<Map<String, Object>> obj : objects) {
                Map<String, Object> oldProps = obj.properties();
                Object rawId = oldProps != null ? oldProps.get("exercise_id") : null;
                // Defensive: a legacy object with missing or non-numeric properties cannot be migrated. Skip it (rather
                // than letting a cast throw and abort the whole run) so the remaining objects still migrate. It is treated
                // like a missing id, not a failure, because such a malformed object could never migrate and would
                // otherwise make the run retry forever and never drop the legacy collection.
                if (!(rawId instanceof Number entityIdNumber)) {
                    log.debug("V0→V1: Skipping object {} with missing or non-numeric exercise_id: {}", obj.uuid(), rawId);
                    skipped++;
                    continue;
                }
                long entityId = entityIdNumber.longValue();
                Map<String, Object> newProps = transformProperties(oldProps);
                String uuid = WeaviateUuidUtil.deterministicUuid(SearchableEntitySchema.TypeValues.EXERCISE, entityId);
                batch.add(WeaviateObject.of(objectBuilder -> objectBuilder.uuid(uuid).properties(newProps)));
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

            hasMore = objects.size() == PAGE_SIZE;
        }

        log.info("V0→V1: Data migration complete — migrated: {}, skipped: {}, failed: {}", migrated, skipped, failed);

        if (failed > 0) {
            throw new IOException("V0→V1: Migration failed for " + failed + " exercises. Aborting cleanup to prevent data loss.");
        }

        // Clean up the legacy collection only after a fully successful migration.
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
     * <li>{@code type} (exercise type) → {@code exercise_type}</li>
     * <li>{@code type = "exercise"} added (entity discriminator)</li>
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

        // In v0, the exercise type was stored in the "type" property using the Java enum name
        // (e.g. "FILE_UPLOAD", "PROGRAMMING"). In v1, "type" is the entity discriminator ("exercise"),
        // and the exercise type is moved to "exercise_type" using ExerciseType.getValue() format
        // (e.g. "file-upload", "programming"). Normalize the value to the v1 format.
        // Note: Some legacy objects might already have "exercise_type" set in the old format.
        Object rawExerciseType = oldProps.get(SearchableEntitySchema.Properties.EXERCISE_TYPE);
        if (rawExerciseType == null) {
            rawExerciseType = oldProps.get("type");
        }

        if (rawExerciseType != null && !SearchableEntitySchema.TypeValues.EXERCISE.equals(rawExerciseType)) {
            String normalized = rawExerciseType.toString().toLowerCase().replace('_', '-');
            newProps.put(SearchableEntitySchema.Properties.EXERCISE_TYPE, normalized);
        }

        // Directly mapped properties (same name in v0 and v1)
        for (String prop : DIRECT_MAPPINGS) {
            Object value = oldProps.get(prop);
            if (value != null) {
                if (DATE_PROPERTIES.contains(prop)) {
                    value = normalizeRfc3339Date(value);
                }
                newProps.put(prop, value);
            }
        }

        // Dropped: course_name (resolved from DB at query time in v1)

        // Remove null entries to keep Weaviate objects clean
        newProps.values().removeIf(Objects::isNull);

        return newProps;
    }

    /**
     * Ensures a date value is an RFC3339 string with the seconds component required by Weaviate.
     * <p>
     * The Weaviate client may return DATE-typed properties as {@link OffsetDateTime} or {@link ZonedDateTime}
     * instead of {@link String}. Passing such objects through unchanged makes the client serialize them via
     * {@code toString()}, which omits zero seconds (e.g. {@code 2026-04-24T20:25Z}) and is rejected by Weaviate
     * with HTTP 422. String values may carry the same defect from the legacy v0 indexing code.
     *
     * @param value the date value from the legacy collection
     * @return the normalized RFC3339 date string
     */
    static String normalizeRfc3339Date(Object value) {
        if (value instanceof OffsetDateTime || value instanceof ZonedDateTime) {
            return WeaviateDateUtil.format((TemporalAccessor) value);
        }
        // Strings from the legacy v0 indexing code (and, defensively, any other type) get the missing-seconds repair
        String dateStr = value.toString();
        Matcher matcher = MISSING_SECONDS.matcher(dateStr);
        if (matcher.find()) {
            return matcher.replaceFirst("$1:00$2");
        }
        return dateStr;
    }

}
