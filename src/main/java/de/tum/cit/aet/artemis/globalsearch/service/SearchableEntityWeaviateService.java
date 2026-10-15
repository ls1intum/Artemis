package de.tum.cit.aet.artemis.globalsearch.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.exercise.domain.event.ExerciseVersionCreatedEvent;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnqueueExecutorConfiguration;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOperation;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
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
import de.tum.cit.aet.artemis.globalsearch.exception.EmbeddingFailedException;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.globalsearch.service.embedding.SearchableEntityEmbeddingClient;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.query.Filter;
import io.weaviate.client6.v1.api.collections.query.Hybrid;
import io.weaviate.client6.v1.api.collections.query.NearVector;

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
 * {@link WeaviateOutboxEntry} row (a plain repository save that joins any ambient transaction) and returns. A
 * request-path row is written on the {@code weaviateOutboxEnqueueExecutor} thread, not on the request thread, so
 * nobody who edits an entity waits for the index bookkeeping; the executor has one thread, which keeps the rows of
 * a node in the order the changes were made, and a request that finds its queue full writes its rows itself. A row
 * that is still queued when the node dies is lost like a failed write is, and the reconcile passes repair both.
 * The single-writer {@code WeaviateOutboxDispatcher} on the scheduling node later claims the row and calls
 * {@link #applyOutboxEntry(WeaviateOutboxEntry)}, which performs the actual Weaviate write with retry. A
 * Weaviate outage or node death therefore no longer loses a write.
 * <p>
 * The {@code Async} method-name suffix is retained so the ~40 call sites stay byte-for-byte unchanged. Enqueue only happens when this bean exists, i.e. when
 * {@link WeaviateEnabled} is true, which
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
    /**
     * Weight of the vector score in hybrid search (1 = vectors only, 0 = keywords only), combined with relative score
     * fusion; the values the chunked embeddings were evaluated with. It must be set explicitly: without it, the Weaviate
     * client sends no alpha and the server falls back to 0, which silently turns hybrid search into keyword search.
     */
    static final float HYBRID_ALPHA = 0.75f;

    private static final String[] QUERY_PROPERTIES = { SearchableEntitySchema.Properties.TITLE + "^3", SearchableEntitySchema.Properties.SHORT_NAME + "^2",
            SearchableEntitySchema.Properties.DESCRIPTION + "^1" };

    /**
     * Safety bound on the number of {@code deleteMany} batches a single bulk delete will issue. Each batch removes
     * up to Weaviate's {@code QUERY_MAXIMUM_RESULTS} cap (10&#8239;000 by default), so this covers scopes far larger
     * than any real course while still terminating a pathological non-converging loop.
     */
    private static final int MAX_DELETE_ITERATIONS = 1000;

    private static final long KEYWORD_FALLBACK_WARNING_INTERVAL_MILLIS = 60_000;

    private final WeaviateService weaviateService;

    private final WeaviateOutboxRepository outboxRepository;

    private final SearchableEntityResolver resolver;

    private final JsonMapper objectMapper;

    private final SearchableEntityContentHasher contentHasher;

    private final ApplicationEventPublisher eventPublisher;

    private final SearchableEntityVectorWriter vectorWriter;

    private final SearchableEntityEmbeddingClient embeddingClient;

    private final Executor enqueueExecutor;

    private final AtomicLong lastKeywordFallbackWarning = new AtomicLong();

    public SearchableEntityWeaviateService(WeaviateService weaviateService, WeaviateOutboxRepository outboxRepository, SearchableEntityResolver resolver, JsonMapper objectMapper,
            SearchableEntityContentHasher contentHasher, ApplicationEventPublisher eventPublisher, SearchableEntityVectorWriter vectorWriter,
            SearchableEntityEmbeddingClient embeddingClient, @Qualifier(WeaviateEnqueueExecutorConfiguration.EXECUTOR_NAME) Executor enqueueExecutor) {
        this.weaviateService = weaviateService;
        this.outboxRepository = outboxRepository;
        this.resolver = resolver;
        this.objectMapper = objectMapper;
        this.contentHasher = contentHasher;
        this.eventPublisher = eventPublisher;
        this.vectorWriter = vectorWriter;
        this.embeddingClient = embeddingClient;
        this.enqueueExecutor = enqueueExecutor;
    }

    // ----- Search path -----

    /**
     * A candidate of the Iris answer path: the hit's properties and, when it can be determined, the passage of the hit
     * that matched the query.
     *
     * @param properties the hit's stored properties
     * @param passage    the matched passage, or null
     */
    public record AnswerCandidate(Map<String, Object> properties, @Nullable String passage) {
    }

    /**
     * Entity recall for the Iris ANSWER path. Unlike {@link #searchSearchableEntities(String, Filter, int)},
     * which boosts titles for palette-style lookups, this search weights all searchable fields
     * neutrally: the answer path's queries are question-shaped ("what are some quizzes ..."), title
     * boosting drags in name-matches over meaning-matches, and the cross-encoder downstream is the
     * component that judges relevance. Recall here should be broad and dumb.
     * <p>
     * Each candidate also carries the passage that matched the query (see {@link SearchableEntityPassages}), which Iris
     * shows its reranker instead of the beginning of a long description.
     *
     * @param query  the user's question
     * @param filter the compound access filter
     * @param limit  the maximum number of candidates
     * @return the candidates with their properties and matched passages
     */
    public List<AnswerCandidate> searchEntityCandidatesForAnswer(String query, Filter filter, int limit) {
        try {
            CollectionHandle<Map<String, Object>> collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            Optional<float[]> queryVector = embedQuery(query);
            List<WeaviateObject<Map<String, Object>>> objects = queryVector.isPresent() ? hybridOrKeyword(collection, query, queryVector.get(), filter, limit, null, true)
                    : keyword(collection, query, filter, limit, null);
            return objects.stream().map(object -> {
                WeaviateDateUtil.normalizeDateProperties(object.properties());
                String passage = queryVector.map(vector -> SearchableEntityPassages.matchedPassage(object.properties(), object.vectors(), vector)).orElse(null);
                return new AnswerCandidate(object.properties(), passage);
            }).toList();
        }
        catch (Exception e) {
            log.error("Failed to search entity candidates (query length={}): {}", query != null ? query.length() : 0, e.getMessage(), e);
            throw new WeaviateException("Failed to search entity candidates in Weaviate: " + e.getMessage(), e);
        }
    }

    /**
     * Performs a single unified search against the {@code SearchableEntities} collection.
     * <p>
     * Uses hybrid (semantic + keyword) search when Artemis computes embeddings, keyword (BM25) search otherwise or
     * when the query cannot be embedded in time. The caller is responsible for passing a compound filter that already
     * encodes the user's per-type access rules. Must be invoked exactly once per user search request.
     *
     * @param query  the search query (may be empty to browse most recent items)
     * @param filter the compound access filter ({@code null} means admin / no filter)
     * @param limit  maximum number of results to return
     * @return the raw property maps returned by Weaviate (caller maps them to response DTOs)
     */
    public List<Map<String, Object>> searchSearchableEntities(String query, Filter filter, int limit) {
        try {
            CollectionHandle<Map<String, Object>> collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            List<WeaviateObject<Map<String, Object>>> objects;
            if (query == null || query.isBlank()) {
                var result = collection.query.fetchObjects(builder -> {
                    builder.limit(limit);
                    if (filter != null) {
                        builder.filters(filter);
                    }
                    return builder;
                });
                objects = result.objects();
            }
            else {
                Optional<float[]> queryVector = embedQuery(query);
                objects = queryVector.isPresent() ? hybridOrKeyword(collection, query, queryVector.get(), filter, limit, QUERY_PROPERTIES, false)
                        : keyword(collection, query, filter, limit, QUERY_PROPERTIES);
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

    /**
     * Embeds a user query; empty when Artemis computes no embeddings or the embedding request fails or times out, in
     * which case the search falls back to keyword search.
     */
    private Optional<float[]> embedQuery(String query) {
        if (!embeddingClient.isEnabled()) {
            return Optional.empty();
        }
        try {
            return Optional.of(embeddingClient.embedQuery(query));
        }
        catch (EmbeddingFailedException e) {
            logKeywordFallback("the query could not be embedded", e);
            return Optional.empty();
        }
    }

    /**
     * Hybrid search with the given query vector (one vector against the rows' chunk vectors, so a row scores by its best
     * chunk). If the hybrid request fails, for example while the collection is still the old single-vector one, the
     * search falls back to keyword search.
     */
    private List<WeaviateObject<Map<String, Object>>> hybridOrKeyword(CollectionHandle<Map<String, Object>> collection, String query, float[] queryVector, Filter filter, int limit,
            String @Nullable [] queryProperties, boolean includeVectors) {
        try {
            return collection.query.hybrid(query, builder -> {
                builder.nearVector(NearVector.of(new float[][] { queryVector })).alpha(HYBRID_ALPHA).fusionType(Hybrid.FusionType.RELATIVE_SCORE).limit(limit);
                if (queryProperties != null) {
                    builder.queryProperties(queryProperties);
                }
                if (filter != null) {
                    builder.filters(filter);
                }
                if (includeVectors) {
                    builder.includeVector();
                }
                return builder;
            }).objects();
        }
        catch (RuntimeException e) {
            logKeywordFallback("the hybrid search failed", e);
            return keyword(collection, query, filter, limit, queryProperties);
        }
    }

    private List<WeaviateObject<Map<String, Object>>> keyword(CollectionHandle<Map<String, Object>> collection, String query, Filter filter, int limit,
            String @Nullable [] queryProperties) {
        return collection.query.bm25(query, builder -> {
            builder.limit(limit);
            if (queryProperties != null) {
                builder.queryProperties(queryProperties);
            }
            if (filter != null) {
                builder.filters(filter);
            }
            return builder;
        }).objects();
    }

    /**
     * Logs a fallback to keyword search at WARN at most once per minute and at DEBUG otherwise, so an outage of the
     * embedding endpoint does not produce one warning per search.
     */
    private void logKeywordFallback(String reason, Exception cause) {
        long now = System.currentTimeMillis();
        long last = lastKeywordFallbackWarning.get();
        if (now - last >= KEYWORD_FALLBACK_WARNING_INTERVAL_MILLIS && lastKeywordFallbackWarning.compareAndSet(last, now)) {
            log.warn("Global search uses keyword search because {}: {}", reason, cause.getMessage());
        }
        else {
            log.debug("Global search uses keyword search because {}: {}", reason, cause.getMessage());
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
        long exerciseId = dto.exerciseId();
        enqueueLive(() -> {
            saveUpsertAndSignal(SearchableEntitySchema.TypeValues.EXERCISE, exerciseId, WeaviateOutboxOrigin.LIVE);
            enqueueChannelOf(SearchableEntitySchema.TypeValues.EXERCISE, exerciseId);
        });
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
        List<Long> exerciseIds = new ArrayList<>(dtos.size());
        for (ExerciseSearchableEntityDTO dto : dtos) {
            if (dto == null || dto.exerciseId() == null) {
                log.warn("Cannot upsert exercise without an ID for exam {}", examId);
                continue;
            }
            exerciseIds.add(dto.exerciseId());
        }
        if (exerciseIds.isEmpty()) {
            return;
        }
        enqueueLive(() -> {
            int enqueued = 0;
            for (Long exerciseId : exerciseIds) {
                if (saveUpsert(SearchableEntitySchema.TypeValues.EXERCISE, exerciseId, WeaviateOutboxOrigin.LIVE)) {
                    enqueued++;
                }
            }
            if (enqueued > 0) {
                log.debug("Enqueued upserts for {} exercises of exam {}", enqueued, examId);
                signalEnqueued();
            }
        });
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.LECTURE, dto.lectureId(), WeaviateOutboxOrigin.LIVE);
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.LECTURE_UNIT, dto.lectureUnitId(), WeaviateOutboxOrigin.LIVE);
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
        long examId = dto.examId();
        enqueueLive(() -> {
            saveUpsertAndSignal(SearchableEntitySchema.TypeValues.EXAM, examId, WeaviateOutboxOrigin.LIVE);
            enqueueChannelOf(SearchableEntitySchema.TypeValues.EXAM, examId);
        });
    }

    /**
     * Rewrites the channel row of an exercise or exam with it: the row holds the date from which students may see the
     * channel, which follows the item's release or visible date. Like {@link #save}, a failure is logged and not thrown:
     * the item's change is already saved and must not turn into an error for the user. The drift sweep repairs the row.
     */
    private void enqueueChannelOf(String type, long entityId) {
        Optional<Long> channelId;
        try {
            channelId = resolver.findChannelIdOf(type, entityId);
        }
        catch (RuntimeException e) {
            log.error("Failed to look up the channel of {} {}; its search row keeps its old visibility date until a reconcile pass repairs it: {}", type, entityId, e.getMessage(),
                    e);
            return;
        }
        channelId.ifPresent(id -> saveUpsertAndSignal(SearchableEntitySchema.TypeValues.CHANNEL, id, WeaviateOutboxOrigin.LIVE));
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.FAQ, dto.faqId(), WeaviateOutboxOrigin.LIVE);
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.CHANNEL, dto.channelId(), WeaviateOutboxOrigin.LIVE);
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.COURSE, dto.courseId(), WeaviateOutboxOrigin.LIVE);
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.POST, dto.postId(), WeaviateOutboxOrigin.LIVE);
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
        enqueueUpsert(SearchableEntitySchema.TypeValues.ANSWER_POST, dto.answerPostId(), WeaviateOutboxOrigin.LIVE);
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
        enqueueDeleteEntity(type, entityId, WeaviateOutboxOrigin.LIVE);
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
    private boolean saveUpsert(String type, Long entityId, WeaviateOutboxOrigin origin) {
        return save(WeaviateOutboxEntry.forUpsert(type, entityId, origin));
    }

    /**
     * Saves an outbox row. A request-path ({@link WeaviateOutboxOrigin#LIVE}) save that fails is logged instead of thrown:
     * the user's change is not undone by it, so it must not turn into an error for the user, just as the previous
     * asynchronous index write never could. The index then stays stale for that entity until it is written again or a
     * reconcile pass repairs it. A reconcile pass keeps the exception, which it relies on to retry the entity next tick.
     *
     * @return whether the row was saved
     */
    private boolean save(WeaviateOutboxEntry entry) {
        if (entry.getOrigin() != WeaviateOutboxOrigin.LIVE) {
            outboxRepository.save(entry);
            return true;
        }
        try {
            outboxRepository.save(entry);
            return true;
        }
        catch (RuntimeException e) {
            log.error("Failed to enqueue {} (params {}); the search index stays stale for it until it is written again or a reconcile pass repairs it: {}", entry,
                    entry.getParams(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Queues an upsert for any entity type, recording which path asked for it.
     * <p>
     * The typed {@code upsert*Async} methods above are the request path and pass {@link WeaviateOutboxOrigin#LIVE}.
     * This generic form exists for a reconcile pass, which works over {@code (type, entityId)} pairs it read from
     * the database or from the index and has no DTO in hand. The row carries identity only either way, so the
     * dispatcher re-derives current state at apply time regardless of who enqueued it.
     *
     * @param type     the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId the database id of the entity
     * @param origin   which path is asking
     */
    public void enqueueUpsert(String type, Long entityId, WeaviateOutboxOrigin origin) {
        if (origin == WeaviateOutboxOrigin.LIVE) {
            enqueueLive(() -> saveUpsertAndSignal(type, entityId, origin));
        }
        else {
            saveUpsertAndSignal(type, entityId, origin);
        }
    }

    private void saveUpsertAndSignal(String type, Long entityId, WeaviateOutboxOrigin origin) {
        if (!saveUpsert(type, entityId, origin)) {
            return;
        }
        log.debug("Enqueued upsert for {} {} ({})", type, entityId, origin);
        signalEnqueued();
    }

    /**
     * Writes outbox rows of a request on the enqueue executor instead of the request thread. A row of a request that fails is logged and never thrown (see
     * {@link #save}), and a task that nevertheless throws must not end the thread that writes the rows of the other requests.
     */
    private void enqueueLive(Runnable enqueue) {
        enqueueExecutor.execute(() -> {
            try {
                enqueue.run();
            }
            catch (RuntimeException e) {
                log.error("Failed to enqueue a search index update; the index stays stale for it until it is written again or a reconcile pass repairs it: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Queues the removal of a single entity's row, recording which path asked for it. Drift and orphan reconcile
     * candidates are re-derived at dispatch, so this queue request writes a current indexable source instead when
     * it has changed since the pass observed it. A LIVE request remains an unconditional delete. See
     * {@link #enqueueUpsert(String, Long, WeaviateOutboxOrigin)} for why the generic form exists.
     *
     * @param type     the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId the database id of the entity
     * @param origin   which path is asking
     */
    public void enqueueDeleteEntity(String type, long entityId, WeaviateOutboxOrigin origin) {
        if (origin == WeaviateOutboxOrigin.LIVE) {
            enqueueLive(() -> saveDeleteEntityAndSignal(type, entityId, origin));
        }
        else {
            saveDeleteEntityAndSignal(type, entityId, origin);
        }
    }

    private void saveDeleteEntityAndSignal(String type, long entityId, WeaviateOutboxOrigin origin) {
        if (!save(WeaviateOutboxEntry.forDeleteEntity(type, entityId, origin))) {
            return;
        }
        log.debug("Enqueued delete for {} {} ({})", type, entityId, origin);
        signalEnqueued();
    }

    private void enqueueBulkDelete(WeaviateOutboxOperation operation, Map<String, Object> params) {
        // Only the request path issues bulk deletes; a reconcile pass works entity by entity.
        enqueueLive(() -> {
            if (!save(WeaviateOutboxEntry.forBulkDelete(operation, serializeMap(params), WeaviateOutboxOrigin.LIVE))) {
                return;
            }
            log.debug("Enqueued {} with params {}", operation, params);
            signalEnqueued();
        });
    }

    /**
     * Publishes a marker event so the dispatcher (scheduling node only) can nudge a drain after the enqueue
     * commits. In-process only: on any other node this has no listener and the scheduled tick drains instead.
     */
    private void signalEnqueued() {
        eventPublisher.publishEvent(new WeaviateOutboxEnqueuedEvent());
    }

    /**
     * Serializes a bulk delete's parameter map to JSON for storage in {@link WeaviateOutboxEntry#getParams()}.
     * Keys are sorted so the stored form is stable and diffable. All values are JSON-native, so the round trip
     * through {@link #deserializeMap(String)} preserves what the delete needs.
     * <p>
     * Content hashing lives in {@link SearchableEntityContentHasher}, which must stay the only implementation
     * so the write path and a later reconcile pass cannot disagree.
     */
    private String serializeMap(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(new TreeMap<>(map));
        }
        catch (JacksonException e) {
            throw new WeaviateException("Failed to serialize Weaviate outbox data: " + e.getMessage(), e);
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
     * {@link Optional#empty()}, so a stale or reordered upsert can never resurrect it. A reconcile-origin
     * {@code DELETE_ENTITY} is re-derived through the same path: a source that became indexable again is upserted,
     * while one that remains absent or non-indexable is deleted. Live per-entity deletes and every bulk delete keep
     * their unconditional deletion semantics, since their callers can enqueue before removing the source entity.
     *
     * @param entry the claimed outbox entry
     * @return the SHA-256 of the property map written for an upsert, or empty when the entry resolved to a delete
     */
    Optional<String> applyOutboxEntry(WeaviateOutboxEntry entry) {
        PreparedEntry prepared = prepareOutboxEntry(entry);
        if (prepared instanceof PreparedUpsert upsert) {
            vectorWriter.embedAndWrite(upsert.row());
            return Optional.of(upsert.contentHash());
        }
        return applyPrepared(prepared, Map.of());
    }

    /**
     * An outbox entry resolved against the database and ready to be applied. Separating this from the write lets the
     * dispatcher embed the missing chunks of several background entries in one request, within its budget.
     */
    sealed interface PreparedEntry permits PreparedUpsert, PreparedOther {

        WeaviateOutboxEntry entry();
    }

    /**
     * An upsert whose row is ready except for the vectors of its missing chunks.
     *
     * @param entry       the outbox entry
     * @param row         the prepared row
     * @param contentHash the hash of the re-derived property map, recorded in the ledger after the write
     */
    record PreparedUpsert(WeaviateOutboxEntry entry, SearchableEntityVectorWriter.PreparedRow row, String contentHash) implements PreparedEntry {
    }

    /**
     * Any other entry: a delete, or an upsert that re-derived to a delete. It needs no embedding.
     *
     * @param entry the outbox entry
     */
    record PreparedOther(WeaviateOutboxEntry entry) implements PreparedEntry {
    }

    /**
     * Resolves an outbox entry against the database. An {@code UPSERT}, and a reconcile-origin {@code DELETE_ENTITY}, re-derive
     * the entity's current desired state ({@link SearchableEntityResolver}); a present, indexable entity becomes a
     * {@link PreparedUpsert}, anything else is applied as a delete. See {@link #applyOutboxEntry} for the semantics.
     *
     * @param entry the claimed outbox entry
     * @return the prepared entry
     */
    PreparedEntry prepareOutboxEntry(WeaviateOutboxEntry entry) {
        boolean rederives = entry.getOperation() == WeaviateOutboxOperation.UPSERT || (entry.getOperation() == WeaviateOutboxOperation.DELETE_ENTITY
                && (entry.getOrigin() == WeaviateOutboxOrigin.RECONCILE_DRIFT || entry.getOrigin() == WeaviateOutboxOrigin.RECONCILE_ORPHAN));
        if (!rederives) {
            return new PreparedOther(entry);
        }
        Optional<Map<String, Object>> desired = resolver.resolve(entry.getEntityType(), entry.getEntityId());
        if (desired.isEmpty()) {
            return new PreparedOther(entry);
        }
        Map<String, Object> properties = desired.get();
        String contentHash = contentHasher.hash(properties);
        Map<String, Object> propertiesToWrite = new HashMap<>(properties);
        propertiesToWrite.put(SearchableEntitySchema.Properties.SOURCE_SEQ, entry.getId());
        propertiesToWrite.put(SearchableEntitySchema.Properties.CONTENT_HASH, contentHash);
        return new PreparedUpsert(entry, vectorWriter.prepare(entry.getEntityType(), entry.getEntityId(), propertiesToWrite), contentHash);
    }

    /**
     * Applies a prepared entry. For an upsert, the vectors of the row's missing chunks must be in {@code embedded}.
     * <p>
     * The written row carries two operational stamps that the re-derived property map does not:
     * {@link SearchableEntitySchema.Properties#SOURCE_SEQ}, equal to this entry's outbox id, so a later bulk delete
     * can fence it (see {@link #writtenBefore}); and {@link SearchableEntitySchema.Properties#CONTENT_HASH}, the
     * same hash recorded in the sync ledger, so a reconcile pass can compare what the index actually holds against
     * what we believe we wrote instead of trusting the ledger alone. Both are excluded from the hash itself, which
     * would otherwise be self-referential or different on every write.
     *
     * @param prepared the prepared entry
     * @param embedded the vectors of the missing chunks of an upsert, by chunk text
     * @return the written content hash for an upsert, or empty when the entry resolved to a delete
     */
    Optional<String> applyPrepared(PreparedEntry prepared, Map<String, float[]> embedded) {
        if (prepared instanceof PreparedUpsert upsert) {
            vectorWriter.write(upsert.row(), embedded);
            return Optional.of(upsert.contentHash());
        }
        WeaviateOutboxEntry entry = prepared.entry();
        switch (entry.getOperation()) {
            case UPSERT, DELETE_ENTITY -> deleteEntityInternal(entry.getEntityType(), entry.getEntityId());
            case DELETE_POSTS_FOR_CHANNEL -> doDeletePostsForChannel(longParam(entry, "channelId"), entry.getId());
            case DELETE_POSTS_FOR_COURSE -> doDeletePostsForCourse(longParam(entry, "courseId"), entry.getId());
            case DELETE_ANSWER_POSTS_FOR_POST -> doDeleteAnswerPostsForPost(longParam(entry, "postId"), entry.getId());
            case DELETE_ALL_FOR_COURSE -> doDeleteAllForCourse(longParam(entry, "courseId"), entry.getId());
            case DELETE_LECTURE_UNITS_FOR_LECTURE -> doDeleteLectureUnitsForLecture(longParam(entry, "lectureId"), entry.getId());
        }
        return Optional.empty();
    }

    private Map<String, Object> deserializeMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        }
        catch (JacksonException e) {
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
     * Deletes the single row of one entity by the same deterministic UUID {@link SearchableEntityVectorWriter} writes it under. A
     * {@code type} filter cannot be used here: the property is word-tokenized, so {@code type Equal "lecture"} also
     * matches {@code lecture_unit} rows (tokens {@code ["lecture", "unit"]}) and {@code type Equal "post"} also matches
     * {@code answer_post} rows, which would delete an unrelated entity that happens to share the numeric id.
     */
    private void deleteEntityInternal(String type, long entityId) {
        try {
            var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            boolean deleted = collection.data.deleteById(WeaviateUuidUtil.deterministicUuid(type, entityId));
            log.debug("Deleted {} {} (row present: {})", type, entityId, deleted);
        }
        catch (IOException e) {
            throw new WeaviateException("Failed to delete " + type + " " + entityId + " in Weaviate: " + e.getMessage(), e);
        }
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
     * Fence for bulk deletes. Matches every row except those whose {@link SearchableEntitySchema.Properties#SOURCE_SEQ}
     * is at least the bulk delete's own outbox id, so rows with a smaller {@code source_seq} and rows without one (written
     * before this fence existed or seeded outside the outbox, which predate every delete) are both in scope.
     * <p>
     * It is phrased as a negation rather than {@code source_seq < id OR source_seq IS NULL} because Weaviate keeps no
     * null state for objects that already existed when a property was added to the collection: on an upgraded
     * installation those rows match neither branch, and a bulk delete would silently skip every one of them.
     * <p>
     * Because a per-entity upsert enqueued after this delete has a larger outbox id, its row's {@code source_seq}
     * is larger and the fence spares it. This closes the last ordering hazard the per-entity collapse cannot: a
     * bulk delete deferred by backoff waking up after a newer in-scope upsert already succeeded and erasing it.
     *
     * @param deleteOutboxId the outbox id of the bulk delete entry being applied
     * @return a filter selecting only rows written strictly before this delete
     */
    private static Filter writtenBefore(long deleteOutboxId) {
        return Filter.not(Filter.property(SearchableEntitySchema.Properties.SOURCE_SEQ).gte(deleteOutboxId));
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
