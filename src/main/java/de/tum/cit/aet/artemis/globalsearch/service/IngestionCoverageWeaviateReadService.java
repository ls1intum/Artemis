package de.tum.cit.aet.artemis.globalsearch.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.aggregate.GroupBy;
import io.weaviate.client6.v1.api.collections.aggregate.GroupedBy;
import io.weaviate.client6.v1.api.collections.query.Filter;
import io.weaviate.client6.v1.api.collections.query.Metadata;

/**
 * Reads the "present" side of the ingestion-coverage diff directly from Weaviate: the exact set of entity ids actually
 * indexed per course (metadata), and the exact set of distinct lecture-unit ids that actually have ingested content in
 * each Iris collection (slides, transcript, segment summaries, unit summaries). It never trusts the write path - it reads
 * reality and diffs against the database elsewhere.
 * <p>
 * Reads are batched, NOT N+1. Metadata is read by chunking the courses and issuing one filtered query per chunk that
 * returns only the id-bearing properties (no bodies), so the whole course set is covered in ~N/50 queries; the
 * same read captures the most recent index-write time per course ({@code last_ingested_at}). Content uses a per-course
 * server-side {@code groupBy(lecture_unit_id)} aggregation, which returns one group per distinct present unit id, bounded
 * by unit count rather than by chunk count - so it is exact without paging through every content object or hitting the
 * {@code QUERY_MAXIMUM_RESULTS} offset cap.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class IngestionCoverageWeaviateReadService {

    private static final Logger log = LoggerFactory.getLogger(IngestionCoverageWeaviateReadService.class);

    /**
     * The metadata entity types diffed exactly. Posts and answer posts are deliberately excluded: they are the
     * high-volume firehose the coverage feature reports as present-only, never id-diffed (design page 18).
     */
    private static final List<String> METADATA_TYPES = List.of(SearchableEntitySchema.TypeValues.EXERCISE, SearchableEntitySchema.TypeValues.LECTURE,
            SearchableEntitySchema.TypeValues.LECTURE_UNIT, SearchableEntitySchema.TypeValues.EXAM, SearchableEntitySchema.TypeValues.FAQ,
            SearchableEntitySchema.TypeValues.CHANNEL, SearchableEntitySchema.TypeValues.COURSE);

    /** Exact, unprefixed name of the Iris slides collection (chunk-grained). */
    public static final String LECTURES_COLLECTION = "Lectures";

    /** Exact, unprefixed name of the Iris transcript collection (segment-grained). */
    public static final String LECTURE_TRANSCRIPTIONS_COLLECTION = "LectureTranscriptions";

    /** Exact, unprefixed name of the Iris segment-summary collection (one summary per aligned segment). */
    public static final String LECTURE_UNIT_SEGMENTS_COLLECTION = "LectureUnitSegments";

    /** Exact, unprefixed name of the Iris unit-summary collection (one summary per lecture unit). */
    public static final String LECTURE_UNITS_COLLECTION = "LectureUnits";

    /** The Iris content collections carry the course id and lecture-unit id under these property names. */
    private static final String CONTENT_COURSE_ID_PROPERTY = "course_id";

    private static final String CONTENT_LECTURE_UNIT_ID_PROPERTY = "lecture_unit_id";

    /** Courses per filtered metadata read; sized so a chunk's objects stay well under {@link #QUERY_MAXIMUM_RESULTS}. */
    private static final int COURSE_CHUNK_SIZE = 50;

    /** Weaviate's default {@code QUERY_MAXIMUM_RESULTS}: {@code offset + limit} may not exceed this. */
    private static final int QUERY_MAXIMUM_RESULTS = 10_000;

    /** Upper bound on distinct content-unit groups returned per course; a course never has this many units. */
    private static final int CONTENT_GROUP_LIMIT = 10_000;

    private final WeaviateService weaviateService;

    public IngestionCoverageWeaviateReadService(WeaviateService weaviateService) {
        this.weaviateService = weaviateService;
    }

    /**
     * The present metadata side of the diff for a set of courses.
     *
     * @param presentIdsByCourseAndType course id -> entity type -> the entity ids present in the Weaviate index
     * @param lastIngestedAtByCourse    course id -> most recent {@code LAST_UPDATE_TIME_UNIX} across the course's objects
     */
    public record PresentMetadata(Map<Long, Map<String, Set<Long>>> presentIdsByCourseAndType, Map<Long, Instant> lastIngestedAtByCourse) {
    }

    /**
     * Reads the exact present metadata id-sets for the given courses, plus each course's most recent index-write time.
     * Batched: the courses are chunked and each chunk is one filtered read of {@code SearchableEntities}
     * returning only {@code (course_id, type, entity_id)} plus the last-update time, so the whole set is covered in
     * ~N/50 queries rather than one per course.
     *
     * @param courseIds the course ids to read (deduplicated internally)
     * @return the present id-sets bucketed by course and type, and {@code last_ingested_at} per course
     */
    public PresentMetadata readPresentMetadata(Collection<Long> courseIds) {
        Map<Long, Map<String, Set<Long>>> presentIds = new HashMap<>();
        Map<Long, Instant> lastIngestedAt = new HashMap<>();
        if (courseIds.isEmpty()) {
            return new PresentMetadata(presentIds, lastIngestedAt);
        }
        CollectionHandle<Map<String, Object>> collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        List<String> returnProperties = List.of(SearchableEntitySchema.Properties.COURSE_ID, SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.Properties.ENTITY_ID);

        for (List<Long> chunk : chunk(courseIds, COURSE_CHUNK_SIZE)) {
            Filter filter = Filter.and(Filter.property(SearchableEntitySchema.Properties.COURSE_ID).containsAny(chunk.toArray(new Long[0])),
                    Filter.property(SearchableEntitySchema.Properties.TYPE).containsAny(METADATA_TYPES.toArray(new String[0])));
            readFilteredChunk(collection, filter, returnProperties, true, object -> {
                Map<String, Object> properties = object.properties();
                Long courseId = readLong(properties.get(SearchableEntitySchema.Properties.COURSE_ID));
                String type = asString(properties.get(SearchableEntitySchema.Properties.TYPE));
                Long entityId = readLong(properties.get(SearchableEntitySchema.Properties.ENTITY_ID));
                if (courseId == null || type == null || entityId == null) {
                    return;
                }
                presentIds.computeIfAbsent(courseId, id -> new HashMap<>()).computeIfAbsent(type, t -> new HashSet<>()).add(entityId);
                Long updatedAtEpochMillis = object.lastUpdatedAt();
                if (updatedAtEpochMillis != null) {
                    Instant candidate = Instant.ofEpochMilli(updatedAtEpochMillis);
                    lastIngestedAt.merge(courseId, candidate, (existing, next) -> next.isAfter(existing) ? next : existing);
                }
            });
        }
        return new PresentMetadata(presentIds, lastIngestedAt);
    }

    /**
     * Reads the exact set of distinct lecture-unit ids that actually have ingested content in the given Iris collection,
     * per course, via a per-course server-side {@code groupBy(lecture_unit_id)} aggregation. Bounded by unit count, not
     * chunk count, so it is exact without shipping bodies or hitting the offset cap. A collection that does not exist on
     * this instance yields an empty result (not an error), so coverage still reports the metadata side.
     *
     * @param collectionName the exact, unprefixed Iris collection name ({@link #LECTURES_COLLECTION} /
     *                           {@link #LECTURE_TRANSCRIPTIONS_COLLECTION})
     * @param courseIds      the course ids to read (deduplicated internally)
     * @return course id -> the distinct lecture-unit ids with content present (courses with none are omitted)
     */
    public Map<Long, Set<Long>> readPresentContentUnitIds(String collectionName, Collection<Long> courseIds) {
        Map<Long, Set<Long>> presentUnitsByCourse = new HashMap<>();
        if (courseIds.isEmpty() || !contentCollectionReadable(collectionName)) {
            return presentUnitsByCourse;
        }
        CollectionHandle<Map<String, Object>> collection = weaviateService.getExternalCollection(collectionName);
        for (Long courseId : new LinkedHashSet<>(courseIds)) {
            Set<Long> unitIds = readDistinctContentUnitIdsForCourse(collection, collectionName, courseId);
            if (!unitIds.isEmpty()) {
                presentUnitsByCourse.put(courseId, unitIds);
            }
        }
        return presentUnitsByCourse;
    }

    private boolean contentCollectionReadable(String collectionName) {
        try {
            return weaviateService.externalCollectionExists(collectionName);
        }
        catch (Exception exception) {
            log.warn("Could not check for Iris content collection '{}' (treating as absent): {}", collectionName, exception.getMessage());
            return false;
        }
    }

    private Set<Long> readDistinctContentUnitIdsForCourse(CollectionHandle<Map<String, Object>> collection, String collectionName, long courseId) {
        try {
            Filter courseFilter = Filter.property(CONTENT_COURSE_ID_PROPERTY).eq(courseId);
            var grouped = collection.aggregate.overAll(aggregation -> aggregation.filters(courseFilter), new GroupBy(CONTENT_LECTURE_UNIT_ID_PROPERTY, CONTENT_GROUP_LIMIT));
            Set<Long> unitIds = new HashSet<>();
            for (var group : grouped.groups()) {
                Long unitId = readGroupLong(group.groupedBy());
                if (unitId != null) {
                    unitIds.add(unitId);
                }
            }
            return unitIds;
        }
        catch (Exception exception) {
            log.warn("Could not read Iris content collection '{}' for course {} (treating as empty): {}", collectionName, courseId, exception.getMessage());
            return Set.of();
        }
    }

    /**
     * Reads a filtered chunk in a single query, applying the consumer to every returned object. A cursor ({@code after})
     * cannot combine with a {@code where} filter, and offset pagination is capped by {@link #QUERY_MAXIMUM_RESULTS}, so a
     * filtered read returns at most that many objects. Chunks are sized to stay under the cap; hitting it means the chunk
     * may be truncated, which is logged so {@link #COURSE_CHUNK_SIZE} can be reduced.
     */
    private void readFilteredChunk(CollectionHandle<Map<String, Object>> collection, Filter filter, List<String> returnProperties, boolean withLastUpdateTime,
            Consumer<WeaviateObject<Map<String, Object>>> consumer) {
        var response = collection.query.fetchObjects(builder -> {
            builder.filters(filter).returnProperties(returnProperties).limit(QUERY_MAXIMUM_RESULTS);
            if (withLastUpdateTime) {
                builder.returnMetadata(Metadata.LAST_UPDATE_TIME_UNIX);
            }
            return builder;
        });
        List<WeaviateObject<Map<String, Object>>> objects = response.objects();
        objects.forEach(consumer);
        if (objects.size() >= QUERY_MAXIMUM_RESULTS) {
            log.warn("A coverage metadata chunk returned the maximum {} objects and may be truncated; reduce COURSE_CHUNK_SIZE.", QUERY_MAXIMUM_RESULTS);
        }
    }

    private static List<List<Long>> chunk(Collection<Long> ids, int size) {
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(ids));
        List<List<Long>> chunks = new ArrayList<>();
        for (int start = 0; start < distinct.size(); start += size) {
            chunks.add(distinct.subList(start, Math.min(start + size, distinct.size())));
        }
        return chunks;
    }

    private static Long readLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            }
            catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    private static Long readGroupLong(GroupedBy<?> groupedBy) {
        if (groupedBy.isInteger() && groupedBy.integer() != null) {
            return groupedBy.integer();
        }
        if (groupedBy.isNumber() && groupedBy.number() != null) {
            return groupedBy.number().longValue();
        }
        if (groupedBy.isText() && groupedBy.text() != null) {
            return readLong(groupedBy.text());
        }
        return null;
    }
}
