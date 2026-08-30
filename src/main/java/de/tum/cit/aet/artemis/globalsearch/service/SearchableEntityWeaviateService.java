package de.tum.cit.aet.artemis.globalsearch.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /**
     * Safety bound on the number of {@code deleteMany} batches a single bulk delete will issue. Each batch removes
     * up to Weaviate's {@code QUERY_MAXIMUM_RESULTS} cap (10&#8239;000 by default), so this covers scopes far larger
     * than any real course while still terminating a pathological non-converging loop.
     */
    private static final int MAX_DELETE_ITERATIONS = 1000;

    private final WeaviateService weaviateService;

    private final WeaviateOutboxRepository outboxRepository;

    private final SearchableEntityResolver resolver;

    private final ObjectMapper objectMapper;

    private final ApplicationEventPublisher eventPublisher;

    private final boolean useHybridSearch;

    public SearchableEntityWeaviateService(WeaviateService weaviateService, WeaviateOutboxRepository outboxRepository, SearchableEntityResolver resolver, ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        this.weaviateService = weaviateService;
        this.outboxRepository = outboxRepository;
        this.resolver = resolver;
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.EXERCISE, dto.exerciseId());
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
        int enqueued = 0;
        for (ExerciseSearchableEntityDTO dto : dtos) {
            if (dto == null || dto.exerciseId() == null) {
                log.warn("Cannot upsert exercise without an ID for exam {}", examId);
                continue;
            }
            saveUpsert(SearchableEntitySchema.TypeValues.EXERCISE, dto.exerciseId());
            enqueued++;
        }
        if (enqueued > 0) {
            log.debug("Enqueued upserts for {} exercises of exam {}", enqueued, examId);
            signalEnqueued();
        }
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.LECTURE, dto.lectureId());
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.LECTURE_UNIT, dto.lectureUnitId());
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.EXAM, dto.examId());
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.FAQ, dto.faqId());
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.CHANNEL, dto.channelId());
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.COURSE, dto.courseId());
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.POST, dto.postId());
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.ANSWER_POST, dto.answerPostId());
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
     * Saves a single UPSERT outbox row without signalling the dispatcher. The row stores only the entity
     * identity; the dispatcher re-derives the current property map from the database when it applies the row.
     * Used both directly (single-entity upserts, followed by a signal) and in the exam-refresh loop.
     */
    private void saveUpsert(String type, Long entityId) {
        outboxRepository.save(WeaviateOutboxEntry.forUpsert(type, entityId));
    }

    private void enqueueUpsert(String type, Long entityId) {
        saveUpsert(type, entityId);
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
            throw new WeaviateException("Failed to serialize Weaviate outbox data: " + e.getMessage(), e);
        }
    }

    /**
     * SHA-256 of the canonical property-map JSON, recorded in the sync ledger so a later reconcile can detect drift.
     */
    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }
        catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every JVM.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // ----- Dispatch path (called by WeaviateOutboxDispatcher on the scheduling node) -----

    /**
     * Applies a claimed outbox entry to Weaviate and reports what it wrote so the dispatcher can refresh the sync
     * ledger. Any failure propagates to the dispatcher, which records a retry with backoff.
     * <p>
     * An {@code UPSERT} does not replay the enqueue-time content: it re-derives the entity's current desired state
     * from the database ({@link SearchableEntityResolver}). A present, indexable entity is upserted and its content
     * hash returned; an entity that has since been deleted or hidden converges to a delete and returns
     * {@link Optional#empty()}, so a stale or reordered upsert can never resurrect it. Delete operations always
     * return empty.
     *
     * @param entry the claimed outbox entry
     * @return the SHA-256 of the property map written for an upsert, or empty when the entry resolved to a delete
     */
    Optional<String> applyOutboxEntry(WeaviateOutboxEntry entry) {
        return switch (entry.getOperation()) {
            case UPSERT -> applyUpsert(entry);
            case DELETE_ENTITY -> {
                deleteEntityInternal(entry.getEntityType(), entry.getEntityId());
                yield Optional.empty();
            }
            case DELETE_POSTS_FOR_CHANNEL -> {
                doDeletePostsForChannel(longParam(entry, "channelId"), entry.getId());
                yield Optional.empty();
            }
            case DELETE_POSTS_FOR_COURSE -> {
                doDeletePostsForCourse(longParam(entry, "courseId"), entry.getId());
                yield Optional.empty();
            }
            case DELETE_ANSWER_POSTS_FOR_POST -> {
                doDeleteAnswerPostsForPost(longParam(entry, "postId"), entry.getId());
                yield Optional.empty();
            }
            case DELETE_ALL_FOR_COURSE -> {
                doDeleteAllForCourse(longParam(entry, "courseId"), entry.getId());
                yield Optional.empty();
            }
            case DELETE_LECTURE_UNITS_FOR_LECTURE -> {
                doDeleteLectureUnitsForLecture(longParam(entry, "lectureId"), entry.getId());
                yield Optional.empty();
            }
        };
    }

    /**
     * Re-derives the entity and either upserts its current property map (returning the written content hash) or,
     * when it no longer exists or is no longer indexable, converges to a delete (returning {@link Optional#empty()}).
     * <p>
     * The written row is stamped with {@link SearchableEntitySchema.Properties#SOURCE_SEQ} equal to this entry's
     * outbox id, so a later bulk delete can fence it (see {@link #writtenBefore}). The stamp is intentionally
     * excluded from the content hash, which is computed over the re-derived property map only, so the sync ledger
     * keeps detecting content drift rather than flapping on every write.
     */
    private Optional<String> applyUpsert(WeaviateOutboxEntry entry) {
        Optional<Map<String, Object>> desired = resolver.resolve(entry.getEntityType(), entry.getEntityId());
        if (desired.isPresent()) {
            Map<String, Object> properties = desired.get();
            String contentHash = sha256Hex(serializeMap(properties));
            Map<String, Object> propertiesToWrite = new HashMap<>(properties);
            propertiesToWrite.put(SearchableEntitySchema.Properties.SOURCE_SEQ, entry.getId());
            upsertRow(entry.getEntityType(), entry.getEntityId(), propertiesToWrite);
            return Optional.of(contentHash);
        }
        deleteEntityInternal(entry.getEntityType(), entry.getEntityId());
        return Optional.empty();
    }

    private Map<String, Object> deserializeMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        }
        catch (JsonProcessingException e) {
            throw new WeaviateException("Failed to deserialize Weaviate outbox data: " + e.getMessage(), e);
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
        deleteManyOrThrow(Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(type), Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).eq(entityId)),
                type + " " + entityId);
    }

    private void doDeleteLectureUnitsForLecture(long lectureId, long deleteOutboxId) {
        deleteManyOrThrow(Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.LECTURE_UNIT),
                Filter.property(SearchableEntitySchema.Properties.LECTURE_ID).eq(lectureId), writtenBefore(deleteOutboxId)), "lecture unit rows for lecture " + lectureId);
    }

    private void doDeleteAnswerPostsForPost(long postId, long deleteOutboxId) {
        deleteManyOrThrow(Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.ANSWER_POST),
                Filter.property(SearchableEntitySchema.Properties.POST_ID).eq(postId), writtenBefore(deleteOutboxId)), "answer post rows for post " + postId);
    }

    private void doDeletePostsForChannel(long channelId, long deleteOutboxId) {
        var typeFilter = Filter.or(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.POST),
                Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.ANSWER_POST));
        deleteManyOrThrow(Filter.and(typeFilter, Filter.property(SearchableEntitySchema.Properties.CHANNEL_ID).eq(channelId), writtenBefore(deleteOutboxId)),
                "post/answer post rows for channel " + channelId);
    }

    private void doDeletePostsForCourse(long courseId, long deleteOutboxId) {
        var typeFilter = Filter.or(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.POST),
                Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.ANSWER_POST));
        deleteManyOrThrow(Filter.and(typeFilter, Filter.property(SearchableEntitySchema.Properties.COURSE_ID).eq(courseId), writtenBefore(deleteOutboxId)),
                "post/answer post rows for course " + courseId);
    }

    private void doDeleteAllForCourse(long courseId, long deleteOutboxId) {
        deleteManyOrThrow(Filter.and(Filter.property(SearchableEntitySchema.Properties.COURSE_ID).eq(courseId), writtenBefore(deleteOutboxId)), "rows for course " + courseId);
    }

    /**
     * Fence for bulk deletes. Matches only rows whose {@link SearchableEntitySchema.Properties#SOURCE_SEQ} is
     * strictly less than the bulk delete's own outbox id, or that carry no {@code source_seq} at all (rows written
     * before this fence existed or seeded outside the outbox, which predate every delete).
     * <p>
     * Because a per-entity upsert enqueued after this delete has a larger outbox id, its row's {@code source_seq}
     * is larger and the fence spares it. This closes the last ordering hazard the per-entity collapse cannot: a
     * bulk delete deferred by backoff waking up after a newer in-scope upsert already succeeded and erasing it.
     *
     * @param deleteOutboxId the outbox id of the bulk delete entry being applied
     * @return a filter selecting only rows written strictly before this delete
     */
    private static Filter writtenBefore(long deleteOutboxId) {
        return Filter.or(Filter.property(SearchableEntitySchema.Properties.SOURCE_SEQ).lt(deleteOutboxId), Filter.property(SearchableEntitySchema.Properties.SOURCE_SEQ).isNull());
    }

    /**
     * Runs a {@code deleteMany} for the whole scope and treats a partial failure as a failure.
     * <p>
     * Weaviate caps a single {@code deleteMany} at {@code QUERY_MAXIMUM_RESULTS} (10&#8239;000 by default), so one
     * call only removes the first batch of a larger scope and reports {@code failed == 0} for it. A busy course or
     * channel can hold far more than that, so this loops until a call matches nothing, draining the scope fully.
     * {@code deleteMany} is idempotent (it deletes by filter), so re-matching already-deleted rows across
     * iterations is safe, and a partial failure ({@code failed > 0}) throws so the dispatcher keeps the outbox row
     * and retries the whole drain with backoff rather than leaving orphaned searchable rows.
     */
    private void deleteManyOrThrow(Filter filter, String description) {
        var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        long totalDeleted = 0;
        for (int iteration = 0; iteration < MAX_DELETE_ITERATIONS; iteration++) {
            var result = collection.data.deleteMany(filter);
            if (result.failed() > 0) {
                throw new WeaviateException("Failed to delete " + result.failed() + " of " + result.matches() + " " + description + " in Weaviate");
            }
            totalDeleted += result.successful();
            if (result.matches() == 0) {
                log.debug("Deleted {} {}", totalDeleted, description);
                return;
            }
        }
        // The dispatcher is the only writer, so nothing adds rows during the drain; not converging within the cap
        // means something is wrong (e.g. rows that keep matching but never delete). Fail so the row is retried.
        throw new WeaviateException("Deletion of " + description + " did not converge within " + MAX_DELETE_ITERATIONS + " batches in Weaviate");
    }
}
