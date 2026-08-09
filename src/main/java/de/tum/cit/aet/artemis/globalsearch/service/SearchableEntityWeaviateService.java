package de.tum.cit.aet.artemis.globalsearch.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.exercise.domain.event.ExerciseVersionCreatedEvent;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOperation;
import de.tum.cit.aet.artemis.globalsearch.dto.WeaviateDateUtil;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.AnswerPostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ChannelSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.CourseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExamSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExerciseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.FaqSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.LectureSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.LectureUnitSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.PostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import io.weaviate.client6.v1.api.WeaviateApiException;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Unified service for synchronizing and searching every indexable entity type (exercise, lecture,
 * lecture unit, exam, FAQ, channel, course, post, answer post) in the shared {@code SearchableEntities}
 * Weaviate collection.
 * <p>
 * The service enforces the single-Weaviate-request invariant for user-facing search: a single
 * {@link #searchSearchableEntities(String, Filter, int)} call is issued per HTTP request, with a compound
 * per-type filter built upstream in {@code GlobalSearchResource}.
 * <p>
 * <b>Durability.</b> Metadata writes are never issued to Weaviate directly from the request path.
 * Each public {@code upsert*Async} / {@code delete*Async} method records the intent as a durable
 * {@link WeaviateOutboxEntry} row (a plain repository save that joins any ambient transaction) and returns.
 * The single-writer {@code WeaviateOutboxDispatcher} on the scheduling node later claims the row and calls
 * {@link #applyOutboxEntry(WeaviateOutboxEntry)}, which performs the actual Weaviate write with retry. A
 * Weaviate outage or node death therefore no longer loses a write.
 * <p>
 * The {@code Async} method-name suffix is retained so the ~40 call sites stay byte-for-byte unchanged; the
 * methods are now synchronous enqueues, so the suffix is a documented slight misnomer (a rename is a trivial
 * follow-up). Enqueue only happens when this bean exists, i.e. when {@link WeaviateEnabled} is true, which
 * preserves the previous feature gating.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityWeaviateService {

    private static final Logger log = LoggerFactory.getLogger(SearchableEntityWeaviateService.class);

    /**
     * Property-level BM25 weights. Title matches count three times as much as description matches,
     * short-name matches (exercises only) count twice as much. Prevents long problem statements from
     * outscoring short, on-point titles.
     */
    private static final String[] QUERY_PROPERTIES = { SearchableEntitySchema.Properties.TITLE + "^3", SearchableEntitySchema.Properties.SHORT_NAME + "^2",
            SearchableEntitySchema.Properties.DESCRIPTION + "^1" };

    private final WeaviateService weaviateService;

    private final WeaviateOutboxRepository outboxRepository;

    private final ObjectMapper objectMapper;

    private final ApplicationEventPublisher eventPublisher;

    private final boolean useHybridSearch;

    public SearchableEntityWeaviateService(WeaviateService weaviateService, WeaviateOutboxRepository outboxRepository, ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        this.weaviateService = weaviateService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.useHybridSearch = weaviateService.isVectorizerAvailable();
    }

    // ----- Search path -----

    /**
     * Performs a single unified search against the {@code SearchableEntities} collection.
     * <p>
     * Uses hybrid (semantic + keyword) search when a vectorizer is available, BM25-only otherwise.
     * The caller is responsible for passing a compound filter that already encodes the user's per-type
     * access rules. Must be invoked exactly once per user search request.
     *
     * @param query  the search query (may be empty to browse most recent items)
     * @param filter the compound access filter ({@code null} means admin / no filter)
     * @param limit  maximum number of results to return
     * @return the raw property maps returned by Weaviate (caller maps them to response DTOs)
     */
    public List<Map<String, Object>> searchSearchableEntities(String query, Filter filter, int limit) {
        try {
            CollectionHandle<Map<String, Object>> collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            boolean browse = query == null || query.isBlank();

            List<WeaviateObject<Map<String, Object>>> objects;
            if (browse) {
                var result = collection.query.fetchObjects(builder -> {
                    builder.limit(limit);
                    if (filter != null) {
                        builder.filters(filter);
                    }
                    return builder;
                });
                objects = result.objects();
            }
            else if (useHybridSearch) {
                var result = collection.query.hybrid(query, builder -> {
                    builder.limit(limit).queryProperties(QUERY_PROPERTIES);
                    if (filter != null) {
                        builder.filters(filter);
                    }
                    return builder;
                });
                objects = result.objects();
            }
            else {
                var result = collection.query.bm25(query, builder -> {
                    builder.limit(limit).queryProperties(QUERY_PROPERTIES);
                    if (filter != null) {
                        builder.filters(filter);
                    }
                    return builder;
                });
                objects = result.objects();
            }

            List<Map<String, Object>> propertiesList = objects.stream().map(WeaviateObject::properties).toList();
            propertiesList.forEach(WeaviateDateUtil::normalizeDateProperties);
            return propertiesList;
        }
        catch (Exception e) {
            log.error("Failed to search SearchableEntities (query length={}): {}", query != null ? query.length() : 0, e.getMessage(), e);
            throw new WeaviateException("Failed to search SearchableEntities in Weaviate: " + e.getMessage(), e);
        }
    }

    // ----- Exercise sync -----

    /**
     * Event listener that synchronizes exercise metadata to Weaviate when a new version is created.
     * Replaces the listener previously hosted on {@code ExerciseWeaviateService}.
     *
     * @param event the exercise version created event
     */
    @EventListener
    public void onExerciseVersionCreated(ExerciseVersionCreatedEvent event) {
        try {
            ExerciseSearchableEntityDTO dto = ExerciseSearchableEntityDTO.fromExercise(event.exercise());
            upsertExerciseAsync(dto);
        }
        catch (Exception e) {
            log.error("Failed to extract exercise DTO for version created event (exercise {}): {}", event.exercise().getId(), e.getMessage(), e);
        }
    }

    /**
     * Enqueues an exercise upsert into the durable outbox.
     *
     * @param dto the extracted exercise data
     */
    public void upsertExerciseAsync(ExerciseSearchableEntityDTO dto) {
        if (dto == null || dto.exerciseId() == null) {
            log.warn("Cannot upsert exercise without an ID");
            return;
        }
        enqueueUpsert(SearchableEntitySchema.TypeValues.EXERCISE, dto.exerciseId(), dto.toPropertyMap());
    }

    /**
     * Enqueues an upsert for each exercise in the list (e.g. from an exam refresh).
     *
     * @param dtos   the list of exercise DTOs to upsert
     * @param examId the exam ID (for logging)
     */
    public void updateExercisesAsync(List<ExerciseSearchableEntityDTO> dtos, long examId) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        for (ExerciseSearchableEntityDTO dto : dtos) {
            if (dto == null || dto.exerciseId() == null) {
                log.warn("Cannot upsert exercise without an ID for exam {}", examId);
                continue;
            }
            saveUpsert(SearchableEntitySchema.TypeValues.EXERCISE, dto.exerciseId(), dto.toPropertyMap());
        }
        log.debug("Enqueued upserts for {} exercises of exam {}", dtos.size(), examId);
        signalEnqueued();
    }

    // ----- Lecture sync -----

    /**
     * Enqueues a lecture upsert into the durable outbox.
     *
     * @param dto the extracted lecture data
     */
    public void upsertLectureAsync(LectureSearchableEntityDTO dto) {
        if (dto == null || dto.lectureId() == null) {
            log.warn("Cannot upsert lecture without an ID");
            return;
        }
        enqueueUpsert(SearchableEntitySchema.TypeValues.LECTURE, dto.lectureId(), dto.toPropertyMap());
    }

    // ----- LectureUnit sync -----

    /**
     * Enqueues a lecture unit upsert into the durable outbox.
     *
     * @param dto the extracted lecture unit data
     */
    public void upsertLectureUnitAsync(LectureUnitSearchableEntityDTO dto) {
        if (dto == null || dto.lectureUnitId() == null) {
            log.warn("Cannot upsert lecture unit without an ID");
            return;
        }
        enqueueUpsert(SearchableEntitySchema.TypeValues.LECTURE_UNIT, dto.lectureUnitId(), dto.toPropertyMap());
    }

    /**
     * Enqueues deletion of every lecture unit row belonging to the given lecture. Invoked from
     * {@code LectureService.delete} so the JPA cascade that removes lecture units doesn't leave
     * orphaned Weaviate rows.
     *
     * @param lectureId the parent lecture id
     */
    public void deleteAllLectureUnitsForLectureAsync(long lectureId) {
        enqueueBulkDelete(WeaviateOutboxOperation.DELETE_LECTURE_UNITS_FOR_LECTURE, Map.of("lectureId", lectureId));
    }

    // ----- Exam sync -----

    /**
     * Enqueues an exam upsert into the durable outbox.
     *
     * @param dto the extracted exam data
     */
    public void upsertExamAsync(ExamSearchableEntityDTO dto) {
        if (dto == null || dto.examId() == null) {
            log.warn("Cannot upsert exam without an ID");
            return;
        }
        enqueueUpsert(SearchableEntitySchema.TypeValues.EXAM, dto.examId(), dto.toPropertyMap());
    }

    // ----- FAQ sync -----

    /**
     * Enqueues a FAQ upsert into the durable outbox.
     *
     * @param dto the extracted FAQ data
     */
    public void upsertFaqAsync(FaqSearchableEntityDTO dto) {
        if (dto == null || dto.faqId() == null) {
            log.warn("Cannot upsert faq without an ID");
            return;
        }
        enqueueUpsert(SearchableEntitySchema.TypeValues.FAQ, dto.faqId(), dto.toPropertyMap());
    }

    // ----- Channel sync -----

    /**
     * Enqueues a channel upsert into the durable outbox.
     *
     * @param dto the extracted channel data
     */
    public void upsertChannelAsync(ChannelSearchableEntityDTO dto) {
        if (dto == null || dto.channelId() == null) {
            log.warn("Cannot upsert channel without an ID");
            return;
        }
        enqueueUpsert(SearchableEntitySchema.TypeValues.CHANNEL, dto.channelId(), dto.toPropertyMap());
    }

    // ----- Course sync -----

    /**
     * Enqueues a course upsert into the durable outbox.
     *
     * @param dto the extracted course data
     */
    public void upsertCourseAsync(CourseSearchableEntityDTO dto) {
        if (dto == null || dto.courseId() == null) {
            log.warn("Cannot upsert course without an ID");
            return;
        }
        enqueueUpsert(SearchableEntitySchema.TypeValues.COURSE, dto.courseId(), dto.toPropertyMap());
    }

    // ----- Post sync -----

    /**
     * Enqueues a post (message) upsert into the durable outbox.
     *
     * @param dto the extracted post data
     */
    public void upsertPostAsync(PostSearchableEntityDTO dto) {
        if (dto == null || dto.postId() == null) {
            log.warn("Cannot upsert post without an ID");
            return;
        }
        enqueueUpsert(SearchableEntitySchema.TypeValues.POST, dto.postId(), dto.toPropertyMap());
    }

    // ----- Answer Post sync -----

    /**
     * Enqueues an answer post (reply) upsert into the durable outbox.
     *
     * @param dto the extracted answer post data
     */
    public void upsertAnswerPostAsync(AnswerPostSearchableEntityDTO dto) {
        if (dto == null || dto.answerPostId() == null) {
            log.warn("Cannot upsert answer post without an ID");
            return;
        }
        enqueueUpsert(SearchableEntitySchema.TypeValues.ANSWER_POST, dto.answerPostId(), dto.toPropertyMap());
    }

    /**
     * Enqueues deletion of every answer post row belonging to the given post. Invoked when
     * a post is deleted so its cascade-deleted replies don't remain orphaned in Weaviate.
     *
     * @param postId the parent post id
     */
    public void deleteAllAnswerPostsForPostAsync(long postId) {
        enqueueBulkDelete(WeaviateOutboxOperation.DELETE_ANSWER_POSTS_FOR_POST, Map.of("postId", postId));
    }

    /**
     * Enqueues deletion of every post row belonging to the given channel. Invoked when
     * a channel is deleted or archived so posts don't remain searchable.
     *
     * @param channelId the channel id
     */
    public void deleteAllPostsForChannelAsync(long channelId) {
        enqueueBulkDelete(WeaviateOutboxOperation.DELETE_POSTS_FOR_CHANNEL, Map.of("channelId", channelId));
    }

    /**
     * Enqueues deletion of every post row belonging to the given course. Invoked when
     * a course is reset so deleted posts don't remain searchable.
     *
     * @param courseId the course id
     */
    public void deleteAllPostsForCourseAsync(long courseId) {
        enqueueBulkDelete(WeaviateOutboxOperation.DELETE_POSTS_FOR_COURSE, Map.of("courseId", courseId));
    }

    // ----- Deletion (generic) -----

    /**
     * Enqueues deletion of a specific entity row from the unified collection.
     *
     * @param type     the entity type (use constants from {@link SearchableEntitySchema.TypeValues})
     * @param entityId the entity id
     */
    public void deleteEntityAsync(String type, long entityId) {
        outboxRepository.save(WeaviateOutboxEntry.forDeleteEntity(type, entityId));
        log.debug("Enqueued delete for {} {}", type, entityId);
        signalEnqueued();
    }

    /**
     * Enqueues deletion of every row belonging to the given course. Invoked from
     * {@code CourseService.delete} so deleted courses don't leave orphaned Weaviate rows.
     *
     * @param courseId the course id
     */
    public void deleteAllForCourseAsync(long courseId) {
        enqueueBulkDelete(WeaviateOutboxOperation.DELETE_ALL_FOR_COURSE, Map.of("courseId", courseId));
    }

    // ----- Enqueue helpers -----

    /**
     * Serializes and saves a single UPSERT outbox row without signalling the dispatcher.
     * Used both directly (single-entity upserts, followed by a signal) and in the exam-refresh loop.
     */
    private void saveUpsert(String type, Long entityId, Map<String, Object> propertyMap) {
        outboxRepository.save(WeaviateOutboxEntry.forUpsert(type, entityId, serializeMap(propertyMap)));
    }

    private void enqueueUpsert(String type, Long entityId, Map<String, Object> propertyMap) {
        saveUpsert(type, entityId, propertyMap);
        log.debug("Enqueued upsert for {} {}", type, entityId);
        signalEnqueued();
    }

    private void enqueueBulkDelete(WeaviateOutboxOperation operation, Map<String, Object> params) {
        outboxRepository.save(WeaviateOutboxEntry.forBulkDelete(operation, serializeMap(params)));
        log.debug("Enqueued {} with params {}", operation, params);
        signalEnqueued();
    }

    /**
     * Publishes a marker event so the dispatcher (scheduling node only) can nudge a drain after the enqueue
     * commits. In-process only: on any other node this has no listener and the scheduled tick drains instead.
     */
    private void signalEnqueued() {
        eventPublisher.publishEvent(new WeaviateOutboxEnqueuedEvent());
    }

    /**
     * Serializes a property/parameter map to canonical JSON (keys sorted) so equal maps hash equal. All map
     * values are JSON-native (strings, numbers, booleans; dates are already RFC3339 strings), so the round
     * trip through {@link #deserializeMap(String)} preserves the values the Weaviate write needs.
     */
    private String serializeMap(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(new TreeMap<>(map));
        }
        catch (JsonProcessingException e) {
            throw new WeaviateException("Failed to serialize Weaviate outbox payload: " + e.getMessage(), e);
        }
    }

    // ----- Dispatch path (called by WeaviateOutboxDispatcher on the scheduling node) -----

    /**
     * Applies a claimed outbox entry to Weaviate. Switches on the entry's operation and calls the matching
     * internal write. Any failure propagates to the dispatcher, which records a retry with backoff.
     *
     * @param entry the claimed outbox entry
     */
    void applyOutboxEntry(WeaviateOutboxEntry entry) {
        switch (entry.getOperation()) {
            case UPSERT -> upsertRow(entry.getEntityType(), entry.getEntityId(), deserializeMap(entry.getPayload()));
            case DELETE_ENTITY -> deleteEntityInternal(entry.getEntityType(), entry.getEntityId());
            case DELETE_POSTS_FOR_CHANNEL -> doDeletePostsForChannel(longParam(entry, "channelId"));
            case DELETE_POSTS_FOR_COURSE -> doDeletePostsForCourse(longParam(entry, "courseId"));
            case DELETE_ANSWER_POSTS_FOR_POST -> doDeleteAnswerPostsForPost(longParam(entry, "postId"));
            case DELETE_ALL_FOR_COURSE -> doDeleteAllForCourse(longParam(entry, "courseId"));
            case DELETE_LECTURE_UNITS_FOR_LECTURE -> doDeleteLectureUnitsForLecture(longParam(entry, "lectureId"));
            default -> throw new IllegalStateException("Unhandled Weaviate outbox operation: " + entry.getOperation());
        }
    }

    private Map<String, Object> deserializeMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        }
        catch (JsonProcessingException e) {
            throw new WeaviateException("Failed to deserialize Weaviate outbox payload: " + e.getMessage(), e);
        }
    }

    private long longParam(WeaviateOutboxEntry entry, String key) {
        Object value = deserializeMap(entry.getParams()).get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Missing or non-numeric parameter '" + key + "' in outbox entry " + entry.getId());
    }

    // ----- Internal Weaviate writes -----

    /**
     * Shared upsert implementation: uses a deterministic UUID derived from {@code (type, entity_id)}
     * to replace an existing row or insert a new one.
     * <br>
     * The deterministic UUID prevents duplicate rows (Weaviate enforces UUID uniqueness), but
     * the {@code exists()} + {@code insert()} sequence is subject to a TOCTOU race: two concurrent
     * callers can both observe {@code exists() == false} and then one {@code insert()} fails with
     * "already exists". We handle this by catching {@link WeaviateApiException} and falling back to
     * {@code replace()}, consistent with the pattern used in {@code V0ToV1Migration} and
     * {@code WeaviateMigrationService}. The deterministic UUID is also what makes re-applying an outbox
     * row idempotent, so retries and duplicate enqueues converge to a single row.
     */
    private void upsertRow(String type, Long entityId, Map<String, Object> properties) {
        try {
            var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            String uuid = WeaviateUuidUtil.deterministicUuid(type, entityId);
            if (collection.data.exists(uuid)) {
                collection.data.replace(uuid, r -> r.properties(properties));
            }
            else {
                try {
                    collection.data.insert(properties, obj -> obj.uuid(uuid));
                }
                catch (WeaviateApiException e) {
                    if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                        collection.data.replace(uuid, r -> r.properties(properties));
                    }
                    else {
                        throw e;
                    }
                }
            }
        }
        catch (IOException e) {
            throw new WeaviateException("Failed to upsert " + type + " " + entityId + " in Weaviate: " + e.getMessage(), e);
        }
    }

    private void deleteEntityInternal(String type, long entityId) {
        var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        collection.data.deleteMany(
                Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(type), Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).eq(entityId)));
    }

    private void doDeleteLectureUnitsForLecture(long lectureId) {
        var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        var filter = Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.LECTURE_UNIT),
                Filter.property(SearchableEntitySchema.Properties.LECTURE_ID).eq(lectureId));
        var result = collection.data.deleteMany(filter);
        log.debug("Deleted {} lecture unit rows for lecture {}", result.successful(), lectureId);
    }

    private void doDeleteAnswerPostsForPost(long postId) {
        var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        var filter = Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.ANSWER_POST),
                Filter.property(SearchableEntitySchema.Properties.POST_ID).eq(postId));
        var result = collection.data.deleteMany(filter);
        log.debug("Deleted {} answer post rows for post {}", result.successful(), postId);
    }

    private void doDeletePostsForChannel(long channelId) {
        var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        var typeFilter = Filter.or(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.POST),
                Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.ANSWER_POST));
        var filter = Filter.and(typeFilter, Filter.property(SearchableEntitySchema.Properties.CHANNEL_ID).eq(channelId));
        var result = collection.data.deleteMany(filter);
        log.debug("Deleted {} post/answer post rows for channel {}", result.successful(), channelId);
    }

    private void doDeletePostsForCourse(long courseId) {
        var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        var typeFilter = Filter.or(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.POST),
                Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.ANSWER_POST));
        var filter = Filter.and(typeFilter, Filter.property(SearchableEntitySchema.Properties.COURSE_ID).eq(courseId));
        var result = collection.data.deleteMany(filter);
        log.debug("Deleted {} post/answer post rows for course {}", result.successful(), courseId);
    }

    private void doDeleteAllForCourse(long courseId) {
        var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        var result = collection.data.deleteMany(Filter.property(SearchableEntitySchema.Properties.COURSE_ID).eq(courseId));
        log.debug("Deleted {} rows for course {}", result.successful(), courseId);
    }
}
