package de.tum.cit.aet.artemis.globalsearch.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.exercise.domain.event.ExerciseVersionCreatedEvent;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.WeaviateDateUtil;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ChannelSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.CourseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExamSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExerciseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.FaqSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.LectureSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.LectureUnitSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.PostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import io.weaviate.client6.v1.api.WeaviateApiException;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Unified service for synchronizing and searching every indexable entity type (exercise, lecture,
 * lecture unit, exam, FAQ, channel) in the shared {@code SearchableEntities} Weaviate collection.
 * <p>
 * The service enforces the single-Weaviate-request invariant for user-facing search: a single
 * {@link #searchSearchableEntities(String, Filter, int)} call is issued per HTTP request, with a compound
 * per-type filter built upstream in {@code GlobalSearchResource}.
 * <p>
 * Upsert/delete helpers are thin wrappers around a shared {@link #upsertRow(String, Long, Map)}
 * implementation that replaces existing rows keyed by {@code (type, entity_id)} or inserts new ones.
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

    private final boolean useHybridSearch;

    public SearchableEntityWeaviateService(WeaviateService weaviateService) {
        this.weaviateService = weaviateService;
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
     * Asynchronously upserts exercise metadata into the unified collection.
     *
     * @param dto the extracted exercise data
     */
    @Async
    public void upsertExerciseAsync(ExerciseSearchableEntityDTO dto) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityUtils.setAuthorizationObject();
        }
        if (dto == null || dto.exerciseId() == null) {
            log.warn("Cannot upsert exercise without an ID");
            return;
        }
        try {
            upsertRow(SearchableEntitySchema.TypeValues.EXERCISE, dto.exerciseId(), dto.toPropertyMap());
            log.debug("Successfully upserted exercise {} '{}' in Weaviate", dto.exerciseId(), dto.exerciseTitle());
        }
        catch (Exception e) {
            log.error("Failed to upsert exercise {} in Weaviate: {}", dto.exerciseId(), e.getMessage(), e);
        }
    }

    /**
     * Asynchronously re-upserts Weaviate metadata for a list of exercises (e.g. from an exam refresh).
     *
     * @param dtos   the list of exercise DTOs to upsert
     * @param examId the exam ID (for logging)
     */
    @Async
    public void updateExercisesAsync(List<ExerciseSearchableEntityDTO> dtos, long examId) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityUtils.setAuthorizationObject();
        }
        if (dtos == null) {
            return;
        }
        log.info("Updating {} exercises for exam {} in Weaviate", dtos.size(), examId);

        int successCount = 0;
        for (ExerciseSearchableEntityDTO dto : dtos) {
            try {
                upsertRow(SearchableEntitySchema.TypeValues.EXERCISE, dto.exerciseId(), dto.toPropertyMap());
                successCount++;
            }
            catch (Exception e) {
                log.error("Failed to update exercise {} in exam {}: {}", dto.exerciseId(), examId, e.getMessage(), e);
            }
        }
        log.info("Successfully updated {} out of {} exercises for exam {} in Weaviate", successCount, dtos.size(), examId);
    }

    // ----- Lecture sync -----

    /**
     * Asynchronously upserts lecture metadata into the unified collection.
     *
     * @param dto the extracted lecture data
     */
    @Async
    public void upsertLectureAsync(LectureSearchableEntityDTO dto) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityUtils.setAuthorizationObject();
        }
        if (dto == null || dto.lectureId() == null) {
            log.warn("Cannot upsert lecture without an ID");
            return;
        }
        try {
            upsertRow(SearchableEntitySchema.TypeValues.LECTURE, dto.lectureId(), dto.toPropertyMap());
            log.debug("Successfully upserted lecture {} '{}' in Weaviate", dto.lectureId(), dto.lectureTitle());
        }
        catch (Exception e) {
            log.error("Failed to upsert lecture {} in Weaviate: {}", dto.lectureId(), e.getMessage(), e);
        }
    }

    // ----- LectureUnit sync -----

    /**
     * Asynchronously upserts a lecture unit into the unified collection.
     *
     * @param dto the extracted lecture unit data
     */
    @Async
    public void upsertLectureUnitAsync(LectureUnitSearchableEntityDTO dto) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityUtils.setAuthorizationObject();
        }
        if (dto == null || dto.lectureUnitId() == null) {
            log.warn("Cannot upsert lecture unit without an ID");
            return;
        }
        try {
            upsertRow(SearchableEntitySchema.TypeValues.LECTURE_UNIT, dto.lectureUnitId(), dto.toPropertyMap());
            log.debug("Successfully upserted lecture unit {} '{}' in Weaviate", dto.lectureUnitId(), dto.unitName());
        }
        catch (Exception e) {
            log.error("Failed to upsert lecture unit {} in Weaviate: {}", dto.lectureUnitId(), e.getMessage(), e);
        }
    }

    /**
     * Asynchronously deletes every lecture unit row belonging to the given lecture. Invoked from
     * {@code LectureService.delete} so the JPA cascade that removes lecture units doesn't leave
     * orphaned Weaviate rows.
     *
     * @param lectureId the parent lecture id
     */
    @Async
    public void deleteAllLectureUnitsForLectureAsync(long lectureId) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityUtils.setAuthorizationObject();
        }
        try {
            var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            var filter = Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.LECTURE_UNIT),
                    Filter.property(SearchableEntitySchema.Properties.LECTURE_ID).eq(lectureId));
            var result = collection.data.deleteMany(filter);
            log.debug("Deleted {} lecture unit rows for lecture {}", result.successful(), lectureId);
        }
        catch (Exception e) {
            log.error("Failed to delete lecture unit rows for lecture {}: {}", lectureId, e.getMessage(), e);
        }
    }

    // ----- Exam sync -----

    /**
     * Asynchronously upserts an exam into the unified collection.
     *
     * @param dto the extracted exam data
     */
    @Async
    public void upsertExamAsync(ExamSearchableEntityDTO dto) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityUtils.setAuthorizationObject();
        }
        if (dto == null || dto.examId() == null) {
            log.warn("Cannot upsert exam without an ID");
            return;
        }
        try {
            upsertRow(SearchableEntitySchema.TypeValues.EXAM, dto.examId(), dto.toPropertyMap());
            log.debug("Successfully upserted exam {} '{}' in Weaviate", dto.examId(), dto.examTitle());
        }
        catch (Exception e) {
            log.error("Failed to upsert exam {} in Weaviate: {}", dto.examId(), e.getMessage(), e);
        }
    }

    // ----- FAQ sync -----

    /**
     * Asynchronously upserts a FAQ into the unified collection.
     *
     * @param dto the extracted FAQ data
     */
    @Async
    public void upsertFaqAsync(FaqSearchableEntityDTO dto) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityUtils.setAuthorizationObject();
        }
        if (dto == null || dto.faqId() == null) {
            log.warn("Cannot upsert faq without an ID");
            return;
        }
        try {
            upsertRow(SearchableEntitySchema.TypeValues.FAQ, dto.faqId(), dto.toPropertyMap());
            log.debug("Successfully upserted faq {} '{}' in Weaviate", dto.faqId(), dto.questionTitle());
        }
        catch (Exception e) {
            log.error("Failed to upsert faq {} in Weaviate: {}", dto.faqId(), e.getMessage(), e);
        }
    }

    // ----- Channel sync -----

    /**
     * Asynchronously upserts a channel into the unified collection.
     *
     * @param dto the extracted channel data
     */
    @Async
    public void upsertChannelAsync(ChannelSearchableEntityDTO dto) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityUtils.setAuthorizationObject();
        }
        if (dto == null || dto.channelId() == null) {
            log.warn("Cannot upsert channel without an ID");
            return;
        }
        try {
            upsertRow(SearchableEntitySchema.TypeValues.CHANNEL, dto.channelId(), dto.toPropertyMap());
            log.debug("Successfully upserted channel {} '{}' in Weaviate", dto.channelId(), dto.name());
        }
        catch (Exception e) {
            log.error("Failed to upsert channel {} in Weaviate: {}", dto.channelId(), e.getMessage(), e);
        }
    }

    // ----- Course sync -----

    /**
     * Asynchronously upserts a course into the unified collection.
     *
     * @param dto the extracted course data
     */
    @Async
    public void upsertCourseAsync(CourseSearchableEntityDTO dto) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityUtils.setAuthorizationObject();
        }
        if (dto == null || dto.courseId() == null) {
            log.warn("Cannot upsert course without an ID");
            return;
        }
        try {
            upsertRow(SearchableEntitySchema.TypeValues.COURSE, dto.courseId(), dto.toPropertyMap());
            log.debug("Successfully upserted course {} '{}' in Weaviate", dto.courseId(), dto.title());
        }
        catch (Exception e) {
            log.error("Failed to upsert course {} in Weaviate: {}", dto.courseId(), e.getMessage(), e);
        }
    }

    // ----- Post sync -----

    /**
     * Asynchronously upserts a post (message) into the unified collection.
     *
     * @param dto the extracted post data
     */
    @Async
    public void upsertPostAsync(PostSearchableEntityDTO dto) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityUtils.setAuthorizationObject();
        }
        if (dto == null || dto.postId() == null) {
            log.warn("Cannot upsert post without an ID");
            return;
        }
        try {
            upsertRow(SearchableEntitySchema.TypeValues.POST, dto.postId(), dto.toPropertyMap());
            log.debug("Successfully upserted post {} in Weaviate", dto.postId());
        }
        catch (Exception e) {
            log.error("Failed to upsert post {} in Weaviate: {}", dto.postId(), e.getMessage(), e);
        }
    }

    /**
     * Asynchronously deletes every post row belonging to the given channel. Invoked when
     * a channel is deleted or archived so posts don't remain searchable.
     *
     * @param channelId the channel id
     */
    @Async
    public void deleteAllPostsForChannelAsync(long channelId) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityUtils.setAuthorizationObject();
        }
        try {
            var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            var filter = Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.POST),
                    Filter.property(SearchableEntitySchema.Properties.CHANNEL_ID).eq(channelId));
            var result = collection.data.deleteMany(filter);
            log.debug("Deleted {} post rows for channel {}", result.successful(), channelId);
        }
        catch (Exception e) {
            log.error("Failed to delete post rows for channel {}: {}", channelId, e.getMessage(), e);
        }
    }

    /**
     * Asynchronously deletes every post row belonging to the given course. Invoked when
     * a course is reset so deleted posts don't remain searchable.
     *
     * @param courseId the course id
     */
    @Async
    public void deleteAllPostsForCourseAsync(long courseId) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityUtils.setAuthorizationObject();
        }
        try {
            var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            var filter = Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(SearchableEntitySchema.TypeValues.POST),
                    Filter.property(SearchableEntitySchema.Properties.COURSE_ID).eq(courseId));
            var result = collection.data.deleteMany(filter);
            log.debug("Deleted {} post rows for course {}", result.successful(), courseId);
        }
        catch (Exception e) {
            log.error("Failed to delete post rows for course {}: {}", courseId, e.getMessage(), e);
        }
    }

    // ----- Deletion (generic) -----

    /**
     * Asynchronously deletes a specific entity row from the unified collection.
     *
     * @param type     the entity type (use constants from {@link SearchableEntitySchema.TypeValues})
     * @param entityId the entity id
     */
    @Async
    public void deleteEntityAsync(String type, long entityId) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityUtils.setAuthorizationObject();
        }
        try {
            deleteEntityInternal(type, entityId);
            log.debug("Deleted {} row for entity id {}", type, entityId);
        }
        catch (Exception e) {
            log.error("Failed to delete {} row for entity id {}: {}", type, entityId, e.getMessage(), e);
        }
    }

    /**
     * Asynchronously deletes every row belonging to the given course. Invoked from
     * {@code CourseService.delete} so deleted courses don't leave orphaned Weaviate rows.
     *
     * @param courseId the course id
     */
    @Async
    public void deleteAllForCourseAsync(long courseId) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            SecurityUtils.setAuthorizationObject();
        }
        try {
            var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            var result = collection.data.deleteMany(Filter.property(SearchableEntitySchema.Properties.COURSE_ID).eq(courseId));
            log.debug("Deleted {} rows for course {}", result.successful(), courseId);
        }
        catch (Exception e) {
            log.error("Failed to delete rows for course {}: {}", courseId, e.getMessage(), e);
        }
    }

    // ----- Internal helpers -----

    /**
     * Shared upsert implementation: uses a deterministic UUID derived from {@code (type, entity_id)}
     * to replace an existing row or insert a new one.
     * <br>
     * The deterministic UUID prevents duplicate rows (Weaviate enforces UUID uniqueness), but
     * the {@code exists()} + {@code insert()} sequence is subject to a TOCTOU race: two concurrent
     * callers (e.g. {@code @Async} methods on different cluster nodes) can both observe
     * {@code exists() == false} and then one {@code insert()} fails with "already exists".
     * We handle this by catching {@link WeaviateApiException} and falling back to {@code replace()},
     * consistent with the pattern used in {@code V0ToV1Migration} and {@code WeaviateMigrationService}.
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
}
