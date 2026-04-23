package de.tum.cit.aet.artemis.globalsearch.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.event.ExerciseVersionCreatedEvent;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableitem.ChannelSearchableItemDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableitem.ExamSearchableItemDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableitem.ExerciseSearchableItemDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableitem.FaqSearchableItemDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableitem.LectureSearchableItemDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableitem.LectureUnitSearchableItemDTO;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import io.weaviate.client6.v1.api.collections.CollectionHandle;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Unified service for synchronizing and searching every indexable entity type (exercise, lecture,
 * lecture unit, exam, FAQ, channel) in the shared {@code SearchableItems} Weaviate collection.
 * <p>
 * The service enforces the single-Weaviate-request invariant for user-facing search: a single
 * {@link #searchSearchableItems(String, Filter, int)} call is issued per HTTP request, with a compound
 * per-type filter built upstream in {@code GlobalSearchResource}.
 * <p>
 * Upsert/delete helpers are thin wrappers around a shared {@link #upsertRow(String, Long, Map)}
 * implementation that replaces existing rows keyed by {@code (type, entity_id)} or inserts new ones.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableItemWeaviateService {

    private static final Logger log = LoggerFactory.getLogger(SearchableItemWeaviateService.class);

    /**
     * Property-level BM25 weights. Title matches count three times as much as description matches,
     * short-name matches (exercises only) count twice as much. Prevents long problem statements from
     * outscoring short, on-point titles.
     */
    private static final String[] QUERY_PROPERTIES = { SearchableEntitySchema.Properties.TITLE + "^3", SearchableEntitySchema.Properties.SHORT_NAME + "^2",
            SearchableEntitySchema.Properties.DESCRIPTION + "^1" };

    private final WeaviateService weaviateService;

    private final boolean useHybridSearch;

    public SearchableItemWeaviateService(WeaviateService weaviateService) {
        this.weaviateService = weaviateService;
        this.useHybridSearch = weaviateService.isVectorizerAvailable();
    }

    // ----- Search path -----

    /**
     * Performs a single unified search against the {@code SearchableItems} collection.
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
    public List<Map<String, Object>> searchSearchableItems(String query, Filter filter, int limit) {
        try {
            CollectionHandle<Map<String, Object>> collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            boolean browse = query == null || query.isBlank();

            if (browse) {
                var result = collection.query.fetchObjects(builder -> {
                    builder.limit(limit);
                    if (filter != null) {
                        builder.filters(filter);
                    }
                    return builder;
                });
                return result.objects().stream().map(WeaviateObject::properties).toList();
            }

            if (useHybridSearch) {
                var result = collection.query.hybrid(query, builder -> {
                    builder.limit(limit).queryProperties(QUERY_PROPERTIES);
                    if (filter != null) {
                        builder.filters(filter);
                    }
                    return builder;
                });
                return result.objects().stream().map(WeaviateObject::properties).toList();
            }

            var result = collection.query.bm25(query, builder -> {
                builder.limit(limit).queryProperties(QUERY_PROPERTIES);
                if (filter != null) {
                    builder.filters(filter);
                }
                return builder;
            });
            return result.objects().stream().map(WeaviateObject::properties).toList();
        }
        catch (Exception e) {
            log.error("Failed to search SearchableItems (query length={}): {}", query != null ? query.length() : 0, e.getMessage(), e);
            throw new WeaviateException("Failed to search SearchableItems in Weaviate: " + e.getMessage(), e);
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
    @Async
    public void onExerciseVersionCreated(ExerciseVersionCreatedEvent event) {
        upsertExerciseAsync(event.exercise());
    }

    /**
     * Asynchronously upserts exercise metadata into the unified collection. Must be called while the
     * Hibernate session is still active so course + exam relationships are loaded before the DTO is
     * extracted.
     *
     * @param exercise the exercise to upsert
     * @throws org.hibernate.LazyInitializationException if required relationships are not loaded
     */
    @Async
    public void upsertExerciseAsync(Exercise exercise) {
        SecurityUtils.setAuthorizationObject();
        if (exercise == null || exercise.getId() == null) {
            log.warn("Cannot upsert exercise without an ID");
            return;
        }
        try {
            ExerciseSearchableItemDTO dto = ExerciseSearchableItemDTO.fromExercise(exercise);
            upsertRow(SearchableEntitySchema.TypeValues.EXERCISE, dto.exerciseId(), dto.toPropertyMap());
            log.debug("Successfully upserted exercise {} '{}' in Weaviate", dto.exerciseId(), dto.exerciseTitle());
        }
        catch (Exception e) {
            log.error("Failed to upsert exercise {} in Weaviate: {}", exercise.getId(), e.getMessage(), e);
        }
    }

    /**
     * Asynchronously re-upserts Weaviate metadata for every exercise belonging to the given exam.
     * Sequential upserts (batching can be added later if this becomes a bottleneck). Exam + exercise
     * groups + exercises must be eagerly loaded.
     *
     * @param exam the exam whose exercises should be refreshed
     * @throws org.hibernate.LazyInitializationException if required relationships are not loaded
     */
    @Async
    public void updateExamExercisesAsync(Exam exam) {
        SecurityUtils.setAuthorizationObject();
        if (exam == null || exam.getExerciseGroups() == null) {
            log.warn("Cannot update exam exercises in Weaviate: exam or exercise groups are null");
            return;
        }
        log.info("Updating {} exercise groups for exam {} in Weaviate", exam.getExerciseGroups().size(), exam.getId());

        List<ExerciseSearchableItemDTO> dtos = new ArrayList<>();
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            for (Exercise exercise : exerciseGroup.getExercises()) {
                try {
                    dtos.add(ExerciseSearchableItemDTO.fromExerciseWithExam(exercise, exam));
                }
                catch (Exception e) {
                    log.error("Failed to convert exercise {} in exam {}: {}", exercise.getId(), exam.getId(), e.getMessage(), e);
                }
            }
        }
        int successCount = 0;
        for (ExerciseSearchableItemDTO dto : dtos) {
            try {
                upsertRow(SearchableEntitySchema.TypeValues.EXERCISE, dto.exerciseId(), dto.toPropertyMap());
                successCount++;
            }
            catch (Exception e) {
                log.error("Failed to update exercise {} in exam {}: {}", dto.exerciseId(), exam.getId(), e.getMessage(), e);
            }
        }
        log.info("Successfully updated {} out of {} exercises for exam {} in Weaviate", successCount, dtos.size(), exam.getId());
    }

    // ----- Lecture sync -----

    /**
     * Asynchronously upserts lecture metadata into the unified collection. Course must be loaded.
     *
     * @param lecture the lecture to upsert
     */
    @Async
    public void upsertLectureAsync(Lecture lecture) {
        SecurityUtils.setAuthorizationObject();
        if (lecture == null || lecture.getId() == null) {
            log.warn("Cannot upsert lecture without an ID");
            return;
        }
        try {
            LectureSearchableItemDTO dto = LectureSearchableItemDTO.fromLecture(lecture);
            upsertRow(SearchableEntitySchema.TypeValues.LECTURE, dto.lectureId(), dto.toPropertyMap());
            log.debug("Successfully upserted lecture {} '{}' in Weaviate", dto.lectureId(), dto.lectureTitle());
        }
        catch (Exception e) {
            log.error("Failed to upsert lecture {} in Weaviate: {}", lecture.getId(), e.getMessage(), e);
        }
    }

    // ----- LectureUnit sync -----

    /**
     * Asynchronously upserts a lecture unit into the unified collection. Only supported subtypes
     * (text, online, attachment/video) are synchronized — exercise units are skipped because their
     * underlying exercise is already indexed under the {@code exercise} type.
     *
     * @param unit the lecture unit to upsert
     */
    @Async
    public void upsertLectureUnitAsync(LectureUnit unit) {
        SecurityUtils.setAuthorizationObject();
        if (unit == null || unit.getId() == null) {
            log.warn("Cannot upsert lecture unit without an ID");
            return;
        }
        if (!LectureUnitSearchableItemDTO.isIndexable(unit)) {
            log.debug("Skipping non-indexable lecture unit type: {}", unit.getClass().getSimpleName());
            return;
        }
        try {
            LectureUnitSearchableItemDTO dto = LectureUnitSearchableItemDTO.fromLectureUnit(unit);
            upsertRow(SearchableEntitySchema.TypeValues.LECTURE_UNIT, dto.lectureUnitId(), dto.toPropertyMap());
            log.debug("Successfully upserted lecture unit {} '{}' in Weaviate", dto.lectureUnitId(), dto.unitName());
        }
        catch (Exception e) {
            log.error("Failed to upsert lecture unit {} in Weaviate: {}", unit.getId(), e.getMessage(), e);
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
        SecurityUtils.setAuthorizationObject();
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
     * Asynchronously upserts an exam into the unified collection. Course must be loaded.
     *
     * @param exam the exam to upsert
     */
    @Async
    public void upsertExamAsync(Exam exam) {
        SecurityUtils.setAuthorizationObject();
        if (exam == null || exam.getId() == null) {
            log.warn("Cannot upsert exam without an ID");
            return;
        }
        try {
            ExamSearchableItemDTO dto = ExamSearchableItemDTO.fromExam(exam);
            upsertRow(SearchableEntitySchema.TypeValues.EXAM, dto.examId(), dto.toPropertyMap());
            log.debug("Successfully upserted exam {} '{}' in Weaviate", dto.examId(), dto.examTitle());
        }
        catch (Exception e) {
            log.error("Failed to upsert exam {} in Weaviate: {}", exam.getId(), e.getMessage(), e);
        }
    }

    // ----- FAQ sync -----

    /**
     * Asynchronously upserts a FAQ into the unified collection. Course must be loaded.
     *
     * @param faq the FAQ to upsert
     */
    @Async
    public void upsertFaqAsync(Faq faq) {
        SecurityUtils.setAuthorizationObject();
        if (faq == null || faq.getId() == null) {
            log.warn("Cannot upsert faq without an ID");
            return;
        }
        try {
            FaqSearchableItemDTO dto = FaqSearchableItemDTO.fromFaq(faq);
            upsertRow(SearchableEntitySchema.TypeValues.FAQ, dto.faqId(), dto.toPropertyMap());
            log.debug("Successfully upserted faq {} '{}' in Weaviate", dto.faqId(), dto.questionTitle());
        }
        catch (Exception e) {
            log.error("Failed to upsert faq {} in Weaviate: {}", faq.getId(), e.getMessage(), e);
        }
    }

    // ----- Channel sync -----

    /**
     * Asynchronously upserts a channel into the unified collection, or deletes any existing row if the
     * channel is no longer indexable (e.g. changed from public to private). Course must be loaded.
     *
     * @param channel the channel to upsert
     */
    @Async
    public void upsertChannelAsync(Channel channel) {
        SecurityUtils.setAuthorizationObject();
        if (channel == null || channel.getId() == null) {
            log.warn("Cannot upsert channel without an ID");
            return;
        }
        try {
            if (!ChannelSearchableItemDTO.isIndexable(channel)) {
                deleteEntityInternal(SearchableEntitySchema.TypeValues.CHANNEL, channel.getId());
                log.debug("Channel {} is no longer indexable (not course-wide and not public); removed from Weaviate", channel.getId());
                return;
            }
            ChannelSearchableItemDTO dto = ChannelSearchableItemDTO.fromChannel(channel);
            upsertRow(SearchableEntitySchema.TypeValues.CHANNEL, dto.channelId(), dto.toPropertyMap());
            log.debug("Successfully upserted channel {} '{}' in Weaviate", dto.channelId(), dto.name());
        }
        catch (Exception e) {
            log.error("Failed to upsert channel {} in Weaviate: {}", channel.getId(), e.getMessage(), e);
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
        SecurityUtils.setAuthorizationObject();
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
        SecurityUtils.setAuthorizationObject();
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
     * Shared upsert implementation: looks up an existing row keyed by {@code (type, entity_id)} and
     * replaces its properties, or inserts a new row otherwise.
     */
    private void upsertRow(String type, Long entityId, Map<String, Object> properties) {
        try {
            var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
            var existing = collection.query.fetchObjects(query -> query
                    .filters(
                            Filter.and(Filter.property(SearchableEntitySchema.Properties.TYPE).eq(type), Filter.property(SearchableEntitySchema.Properties.ENTITY_ID).eq(entityId)))
                    .limit(1));
            if (!existing.objects().isEmpty()) {
                String uuid = existing.objects().getFirst().uuid();
                collection.data.replace(uuid, r -> r.properties(properties));
            }
            else {
                collection.data.insert(properties);
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
