package de.tum.cit.aet.artemis.globalsearch.service;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.LectureSchema;
import de.tum.cit.aet.artemis.globalsearch.dto.LectureWeaviateDTO;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import io.weaviate.client6.v1.api.collections.WeaviateObject;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Service for synchronizing lecture metadata with Weaviate vector database.
 * This service handles insert, update, and delete operations for lectures in Weaviate.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class LectureWeaviateService {

    private static final Logger log = LoggerFactory.getLogger(LectureWeaviateService.class);

    private final WeaviateService weaviateService;

    private final boolean useHybridSearch;

    public LectureWeaviateService(WeaviateService weaviateService) {
        this.weaviateService = weaviateService;
        this.useHybridSearch = weaviateService.isVectorizerAvailable();
    }

    /**
     * Performs an upsert operation: queries for existing object and replaces it, or inserts if not found.
     *
     * @param lectureWeaviateDTO the lecture data to upsert
     * @throws WeaviateException if the operation fails
     */
    private void upsertLectureInWeaviate(LectureWeaviateDTO lectureWeaviateDTO) throws WeaviateException {
        try {
            var collection = weaviateService.getCollection(LectureSchema.COLLECTION_NAME);

            var existingObjectQueryResult = collection.query
                    .fetchObjects(query -> query.filters(Filter.property(LectureSchema.Properties.LECTURE_ID).eq(lectureWeaviateDTO.lectureId())).limit(1));

            Map<String, Object> properties = buildLectureProperties(lectureWeaviateDTO);

            if (!existingObjectQueryResult.objects().isEmpty()) {
                var existingObject = existingObjectQueryResult.objects().getFirst();
                String uuid = existingObject.uuid();
                collection.data.replace(uuid, r -> r.properties(properties));
                log.debug("Replaced existing lecture {} with UUID {}", lectureWeaviateDTO.lectureId(), uuid);
            }
            else {
                collection.data.insert(properties);
                log.debug("Inserted new lecture {}", lectureWeaviateDTO.lectureId());
            }
        }
        catch (IOException e) {
            log.error("Failed to upsert lecture {} in Weaviate: {}", lectureWeaviateDTO.lectureId(), e.getMessage(), e);
            throw new WeaviateException("Failed to upsert lecture " + lectureWeaviateDTO.lectureId() + " in Weaviate: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the complete property map for a lecture.
     *
     * @param lectureWeaviateDTO the lecture data
     * @return the property map ready for Weaviate
     */
    private Map<String, Object> buildLectureProperties(LectureWeaviateDTO lectureWeaviateDTO) {
        Map<String, Object> properties = new HashMap<>();

        properties.put(LectureSchema.Properties.LECTURE_ID, lectureWeaviateDTO.lectureId());
        properties.put(LectureSchema.Properties.COURSE_ID, lectureWeaviateDTO.courseId());
        properties.put(LectureSchema.Properties.TITLE, lectureWeaviateDTO.lectureTitle());
        properties.put(LectureSchema.Properties.IS_TUTORIAL_LECTURE, lectureWeaviateDTO.isTutorialLecture());

        if (lectureWeaviateDTO.courseTitle() != null) {
            properties.put(LectureSchema.Properties.COURSE_NAME, lectureWeaviateDTO.courseTitle());
        }
        if (lectureWeaviateDTO.description() != null) {
            properties.put(LectureSchema.Properties.DESCRIPTION, lectureWeaviateDTO.description());
        }
        if (lectureWeaviateDTO.startDate() != null) {
            properties.put(LectureSchema.Properties.START_DATE, formatDate(lectureWeaviateDTO.startDate()));
        }
        if (lectureWeaviateDTO.endDate() != null) {
            properties.put(LectureSchema.Properties.END_DATE, formatDate(lectureWeaviateDTO.endDate()));
        }

        return properties;
    }

    /**
     * Asynchronously upserts (inserts or updates) lecture metadata in Weaviate.
     * This method executes in a separate thread to avoid blocking the HTTP request thread.
     * IMPORTANT: The lecture entity must have its course relationship eagerly loaded before calling this method.
     *
     * @param lecture the lecture to upsert (must have course relationship loaded)
     * @throws org.hibernate.LazyInitializationException if required relationships are not loaded
     */
    @Async
    public void upsertLectureAsync(Lecture lecture) {
        SecurityUtils.setAuthorizationObject();

        if (lecture.getId() == null) {
            log.warn("Cannot upsert lecture without an ID");
            return;
        }

        try {
            LectureWeaviateDTO data = LectureWeaviateDTO.fromLecture(lecture);
            upsertLectureInWeaviate(data);
            log.debug("Successfully upserted lecture {} '{}' in Weaviate", data.lectureId(), data.lectureTitle());
        }
        catch (Exception e) {
            log.error("Failed to upsert lecture {} in Weaviate: {}", lecture.getId(), e.getMessage(), e);
        }
    }

    /**
     * Asynchronously deletes lecture metadata from Weaviate.
     * This method executes in a separate thread to avoid blocking the HTTP request thread.
     *
     * @param lectureId the ID of the lecture to delete
     */
    @Async
    public void deleteLectureAsync(long lectureId) {
        SecurityUtils.setAuthorizationObject();

        try {
            deleteLectureFromWeaviate(lectureId);
            log.debug("Successfully deleted lecture {} from Weaviate", lectureId);
        }
        catch (Exception e) {
            log.error("Failed to delete lecture {} from Weaviate: {}", lectureId, e.getMessage(), e);
        }
    }

    /**
     * Deletes all lecture entries for the given lecture ID from the Weaviate collection.
     *
     * @param lectureId the lecture ID
     */
    private void deleteLectureFromWeaviate(long lectureId) {
        var collection = weaviateService.getCollection(LectureSchema.COLLECTION_NAME);
        var deleteResult = collection.data.deleteMany(Filter.property(LectureSchema.Properties.LECTURE_ID).eq(lectureId));
        log.debug("Deleted {} lecture entries for lecture ID {}", deleteResult.successful(), lectureId);
    }

    /**
     * Formats a ZonedDateTime to RFC3339 format required by Weaviate.
     *
     * @param dateTime the date time to format
     * @return the formatted date string
     */
    private String formatDate(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * Performs a search on lectures using a pre-built filter.
     * Uses hybrid (semantic + keyword) search when a vectorizer is available,
     * or falls back to BM25 (keyword-only) search otherwise.
     *
     * @param query  the search query
     * @param filter the Weaviate filter (may be null for no filtering)
     * @param limit  maximum number of results
     * @return list of lecture search results from Weaviate
     */
    public List<Map<String, Object>> searchLecturesWithFilter(String query, Filter filter, int limit) {
        try {
            var collection = weaviateService.getCollection(LectureSchema.COLLECTION_NAME);

            if (useHybridSearch) {
                var result = collection.query.hybrid(query, hybridQueryBuilder -> {
                    hybridQueryBuilder.limit(limit);
                    if (filter != null) {
                        hybridQueryBuilder.filters(filter);
                    }
                    return hybridQueryBuilder;
                });
                return result.objects().stream().map(WeaviateObject::properties).toList();
            }
            else {
                var result = collection.query.bm25(query, bm25QueryBuilder -> {
                    bm25QueryBuilder.limit(limit);
                    if (filter != null) {
                        bm25QueryBuilder.filters(filter);
                    }
                    return bm25QueryBuilder;
                });
                return result.objects().stream().map(WeaviateObject::properties).toList();
            }
        }
        catch (Exception e) {
            log.error("Failed to search lectures (query length={}): {}", query != null ? query.length() : 0, e.getMessage(), e);
            throw new WeaviateException("Failed to search lectures in Weaviate: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches lectures using a pre-built filter (no search query).
     *
     * @param filter the Weaviate filter (may be null for no filtering)
     * @param limit  maximum number of results
     * @return list of lecture results from Weaviate
     */
    public List<Map<String, Object>> fetchLecturesWithFilter(Filter filter, int limit) {
        try {
            var collection = weaviateService.getCollection(LectureSchema.COLLECTION_NAME);

            var result = collection.query.fetchObjects(queryBuilder -> {
                queryBuilder.limit(limit);
                if (filter != null) {
                    queryBuilder.filters(filter);
                }
                return queryBuilder;
            });

            return result.objects().stream().map(WeaviateObject::properties).toList();
        }
        catch (Exception e) {
            log.error("Failed to fetch lectures: {}", e.getMessage(), e);
            throw new WeaviateException("Failed to fetch lectures from Weaviate: " + e.getMessage(), e);
        }
    }
}
