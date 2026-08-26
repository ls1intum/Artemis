package de.tum.cit.aet.artemis.globalsearch.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedContentObjectDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IndexedEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.query.Filter;
import io.weaviate.client6.v1.api.collections.query.Metadata;

/**
 * Reads the objects one course actually has in the index, for the admin content browser.
 * <p>
 * The sibling {@link IngestionCoverageWeaviateReadService} answers "how many, across every course" and deliberately
 * never ships a body. This service answers "show me what is in there for this one course", so it does ship bodies, and
 * every read here is therefore scoped to a single course and bounded.
 * <p>
 * The browser opens on a course and drills down, so the reads are split to match: presence is read for the whole course
 * at once because the tree needs all of it to draw, and objects are read only for the node the admin selects. That split
 * is not just frugality. The Iris content collections are chunk-grained, so one lecture unit can hold hundreds of
 * objects; inferring "this unit has slides" from a capped read of the course's objects would silently under-report
 * content for any course above the cap, which is most of them. Presence is read as a distinct unit set instead, which is
 * bounded by unit count and exact at any size.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class IngestionBrowserWeaviateReadService {

    private static final Logger log = LoggerFactory.getLogger(IngestionBrowserWeaviateReadService.class);

    /**
     * The browser's stable content keys, mapped to the exact (unprefixed) Iris collection each one is stored in. The
     * keys are the browser's own vocabulary and cross the wire; the collection names belong to the Iris pipeline.
     */
    public static final String KEY_SLIDES = "slides";

    public static final String KEY_TRANSCRIPT = "transcript";

    public static final String KEY_UNIT_SUMMARY = "unit_summary";

    public static final String KEY_SEGMENTS = "segments";

    private static final Map<String, String> CONTENT_COLLECTION_BY_KEY = Map.of(KEY_SLIDES, IngestionCoverageWeaviateReadService.LECTURES_COLLECTION, KEY_TRANSCRIPT,
            IngestionCoverageWeaviateReadService.LECTURE_TRANSCRIPTIONS_COLLECTION, KEY_UNIT_SUMMARY, IngestionCoverageWeaviateReadService.LECTURE_UNITS_COLLECTION, KEY_SEGMENTS,
            IngestionCoverageWeaviateReadService.LECTURE_UNIT_SEGMENTS_COLLECTION);

    /** Fixed display order for the content keys, so the tree lists a unit's collections the same way every time. */
    private static final List<String> CONTENT_KEY_ORDER = List.of(KEY_SLIDES, KEY_TRANSCRIPT, KEY_UNIT_SUMMARY, KEY_SEGMENTS);

    /** The only properties the tree needs to place a row. Everything else is read when a row is actually selected. */
    private static final String[] TREE_PROPERTIES = { SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.Properties.ENTITY_ID, SearchableEntitySchema.Properties.TITLE,
            SearchableEntitySchema.Properties.LECTURE_ID };

    /**
     * Upper bound on the metadata rows read for one course. A course's browsable metadata runs to tens or low hundreds
     * of rows, so this is headroom rather than a working limit; it exists so a course with unexpected data cannot pull an
     * unbounded response.
     */
    private static final int ENTITY_READ_LIMIT = 2_000;

    /** Upper bound on the content objects read for one lecture unit in one collection. */
    private static final int UNIT_CONTENT_READ_LIMIT = 500;

    /** The Iris content collections carry the course id and lecture-unit id under these property names. */
    private static final String CONTENT_COURSE_ID_PROPERTY = "course_id";

    private static final String CONTENT_LECTURE_UNIT_ID_PROPERTY = "lecture_unit_id";

    private final WeaviateService weaviateService;

    public IngestionBrowserWeaviateReadService(WeaviateService weaviateService) {
        this.weaviateService = weaviateService;
    }

    /**
     * The content keys the browser knows about, in display order.
     *
     * @return the content keys
     */
    public static List<String> contentKeys() {
        return CONTENT_KEY_ORDER;
    }

    /**
     * Whether a content key names a collection the browser can read.
     *
     * @param contentKey the key to check
     * @return {@code true} if the key is known
     */
    public static boolean isKnownContentKey(String contentKey) {
        return CONTENT_COLLECTION_BY_KEY.containsKey(contentKey);
    }

    /**
     * Reads the {@code SearchableEntities} rows stored for a course, each with the properties Weaviate actually holds.
     * <p>
     * Restricted to the types the coverage matrix measures, which excludes posts and answer posts. That is what makes the
     * read bounded: a busy course has millions of posts, and enumerating them would both dwarf the response and crowd the
     * lectures and units the tree is built from out of it entirely.
     * <p>
     * Only the properties the tree places a row by are requested. Asking for the whole row meant every entity carried its
     * body text, which is the bulk of what a course stores and none of what a list of titles needs.
     *
     * @param courseId the course to read
     * @return the stored rows, in no particular order
     * @throws WeaviateException if the collection cannot be read
     */
    public List<IndexedEntityDTO> listIndexedEntitiesForCourse(long courseId) {
        try {
            CollectionHandle<Map<String, Object>> collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            Filter filter = Filter.and(Filter.property(SearchableEntitySchema.Properties.COURSE_ID).eq(courseId),
                    Filter.property(SearchableEntitySchema.Properties.TYPE).containsAny(IngestionCoverageWeaviateReadService.METADATA_TYPES.toArray(new String[0])));

            var response = collection.query
                    .fetchObjects(builder -> builder.filters(filter).limit(ENTITY_READ_LIMIT).returnProperties(TREE_PROPERTIES).returnMetadata(Metadata.CREATION_TIME_UNIX));
            List<WeaviateObject<Map<String, Object>>> objects = response.objects();
            if (objects.size() >= ENTITY_READ_LIMIT) {
                log.warn("Course {} returned the maximum {} indexed entities; the content browser is showing a truncated view.", courseId, ENTITY_READ_LIMIT);
            }

            List<IndexedEntityDTO> entities = new ArrayList<>(objects.size());
            for (WeaviateObject<Map<String, Object>> object : objects) {
                Map<String, Object> properties = object.properties();
                String type = asString(properties.get(SearchableEntitySchema.Properties.TYPE));
                Long entityId = asLong(properties.get(SearchableEntitySchema.Properties.ENTITY_ID));
                if (type == null || entityId == null) {
                    // A row without its own identity cannot be placed in the tree or matched to a database entity.
                    log.warn("Skipping a malformed SearchableEntities row {} for course {}: it has no type or entity id.", object.uuid(), courseId);
                    continue;
                }
                entities.add(new IndexedEntityDTO(type, entityId, asString(properties.get(SearchableEntitySchema.Properties.TITLE)),
                        asLong(properties.get(SearchableEntitySchema.Properties.LECTURE_ID)), creationTime(object)));
            }
            return entities;
        }
        catch (Exception exception) {
            throw new WeaviateException("Failed to read the indexed entities for course " + courseId + ": " + exception.getMessage(), exception);
        }
    }

    /**
     * Reads the content objects stored for one lecture unit in one Iris collection, with their stored properties. This
     * is the leaf of the browser: the admin has selected a collection under a unit and wants to see the records.
     *
     * @param courseId   the course the unit belongs to, also filtered on so a mismatched pair returns nothing
     * @param unitId     the lecture unit to read
     * @param contentKey the content key naming the collection
     * @return the stored objects, capped at {@link #UNIT_CONTENT_READ_LIMIT}; empty if the collection does not exist here
     * @throws IllegalArgumentException if the content key is not one the browser knows
     */
    public List<IndexedContentObjectDTO> listContentObjectsForUnit(long courseId, long unitId, String contentKey) {
        String collectionName = CONTENT_COLLECTION_BY_KEY.get(contentKey);
        if (collectionName == null) {
            throw new IllegalArgumentException("Unknown content key '" + contentKey + "'; expected one of " + CONTENT_KEY_ORDER);
        }
        if (!contentCollectionReadable(collectionName)) {
            return List.of();
        }
        try {
            CollectionHandle<Map<String, Object>> collection = weaviateService.getExternalCollection(collectionName);
            Filter filter = Filter.and(Filter.property(CONTENT_COURSE_ID_PROPERTY).eq(courseId), Filter.property(CONTENT_LECTURE_UNIT_ID_PROPERTY).eq(unitId));

            var response = collection.query.fetchObjects(builder -> builder.filters(filter).limit(UNIT_CONTENT_READ_LIMIT).returnMetadata(Metadata.CREATION_TIME_UNIX));
            List<WeaviateObject<Map<String, Object>>> objects = response.objects();
            if (objects.size() >= UNIT_CONTENT_READ_LIMIT) {
                log.warn("Lecture unit {} returned the maximum {} objects in '{}'; the content browser is showing a truncated view.", unitId, UNIT_CONTENT_READ_LIMIT,
                        collectionName);
            }
            return objects.stream().map(object -> new IndexedContentObjectDTO(creationTime(object), populatedOnly(object.properties()))).toList();
        }
        catch (Exception exception) {
            throw new WeaviateException("Failed to read '" + contentKey + "' content for lecture unit " + unitId + " in course " + courseId + ": " + exception.getMessage(),
                    exception);
        }
    }

    /**
     * Whether an Iris collection exists on this instance. Absent is a normal state (Iris may never have run here), so it
     * reads as empty rather than as a failure.
     */
    private boolean contentCollectionReadable(String collectionName) {
        try {
            return weaviateService.externalCollectionExists(collectionName);
        }
        catch (Exception exception) {
            log.warn("Could not check for Iris content collection '{}' (treating as absent): {}", collectionName, exception.getMessage());
            return false;
        }
    }

    /**
     * Drops the properties the object has no value for. The metadata schema is a wide sparse superset shared by every
     * entity type, so an untrimmed row is mostly absent fields, and the browser renders the populated ones only.
     */
    private static Map<String, Object> populatedOnly(Map<String, Object> properties) {
        Map<String, Object> populated = new LinkedHashMap<>();
        properties.forEach((key, value) -> {
            if (value != null && !(value instanceof String text && text.isBlank())) {
                populated.put(key, value);
            }
        });
        return populated;
    }

    private static Instant creationTime(WeaviateObject<Map<String, Object>> object) {
        Long createdAtEpochMillis = object.createdAt();
        return createdAtEpochMillis == null ? null : Instant.ofEpochMilli(createdAtEpochMillis);
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
