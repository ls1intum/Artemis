package de.tum.cit.aet.artemis.globalsearch.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.SearchableEntityCandidateDTO;

/**
 * Pre-fetches entity candidates for the Iris global-search answer path. Runs the SAME filtered
 * hybrid search as the palette (one Weaviate request, access rules from
 * {@link SearchableEntityAccessFilterService}), then maps the raw rows into typed candidates with
 * resolved course names and deep links. Pyris renders the candidates into text cards, reranks them
 * against the lecture content, and answers from the mixed pool; it never queries the entity
 * collection itself, because channel membership, exam registrations and role-dependent release
 * rules only exist in the Artemis database.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityPrefetchService {

    /** All indexed types take part in the answer path. */
    private static final Set<String> PREFETCH_TYPES = Set.of(SearchableEntitySchema.TypeValues.EXERCISE, SearchableEntitySchema.TypeValues.LECTURE,
            SearchableEntitySchema.TypeValues.LECTURE_UNIT, SearchableEntitySchema.TypeValues.EXAM, SearchableEntitySchema.TypeValues.FAQ,
            SearchableEntitySchema.TypeValues.CHANNEL, SearchableEntitySchema.TypeValues.COURSE, SearchableEntitySchema.TypeValues.POST,
            SearchableEntitySchema.TypeValues.ANSWER_POST);

    private final SearchableEntityAccessFilterService accessFilterService;

    private final SearchableEntityWeaviateService searchableEntityWeaviateService;

    private final CourseRepository courseRepository;

    public SearchableEntityPrefetchService(SearchableEntityAccessFilterService accessFilterService, SearchableEntityWeaviateService searchableEntityWeaviateService,
            CourseRepository courseRepository) {
        this.accessFilterService = accessFilterService;
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
        this.courseRepository = courseRepository;
    }

    /**
     * Runs the access-filtered entity search for the given user and maps the rows to candidates.
     *
     * @param user     the requesting user (with course roles loaded)
     * @param query    the search query
     * @param limit    the maximum number of candidates
     * @param courseId optional course id to scope the candidates to a single course
     * @return the candidates, empty when the user has no accessible courses
     */
    public List<SearchableEntityCandidateDTO> prefetchCandidates(User user, String query, int limit, @Nullable Long courseId) {
        var filterResult = accessFilterService.buildSearchableItemFilter(user, courseId == null ? null : List.of(courseId), null, PREFETCH_TYPES);
        if (!filterResult.hasAccess()) {
            return List.of();
        }
        List<Map<String, Object>> rawResults = searchableEntityWeaviateService.searchEntityCandidatesForAnswer(query, filterResult.filter(), limit);
        Map<Long, String> courseNameById = resolveCourseNames(rawResults, filterResult);
        List<SearchableEntityCandidateDTO> candidates = new ArrayList<>(rawResults.size());
        for (Map<String, Object> properties : rawResults) {
            candidates.add(toCandidate(properties, courseNameById));
        }
        return candidates;
    }

    private Map<Long, String> resolveCourseNames(List<Map<String, Object>> rawResults, SearchableEntityAccessFilterService.FilterBuildResult filterResult) {
        if (filterResult.accessibleCoursesById() != null) {
            Map<Long, String> names = new java.util.HashMap<>();
            filterResult.accessibleCoursesById().forEach((id, course) -> names.put(id, course.getTitle()));
            return names;
        }
        // Admin path: the filter carries no course map, resolve only the hit courses.
        Set<Long> courseIds = new HashSet<>();
        for (Map<String, Object> properties : rawResults) {
            Long courseId = asLong(properties.get(SearchableEntitySchema.Properties.COURSE_ID));
            if (courseId != null) {
                courseIds.add(courseId);
            }
        }
        Map<Long, String> names = new java.util.HashMap<>();
        courseRepository.findAllById(courseIds).forEach(course -> names.put(course.getId(), course.getTitle()));
        return names;
    }

    private static SearchableEntityCandidateDTO toCandidate(Map<String, Object> properties, Map<Long, String> courseNameById) {
        String entityType = asString(properties.get(SearchableEntitySchema.Properties.TYPE));
        Long entityId = asLong(properties.get(SearchableEntitySchema.Properties.ENTITY_ID));
        Long courseId = asLong(properties.get(SearchableEntitySchema.Properties.COURSE_ID));
        Long lectureId = asLong(properties.get(SearchableEntitySchema.Properties.LECTURE_ID));
        Long examId = asLong(properties.get(SearchableEntitySchema.Properties.EXAM_ID));
        Long channelId = asLong(properties.get(SearchableEntitySchema.Properties.CHANNEL_ID));
        return new SearchableEntityCandidateDTO(entityType, entityId, courseId, courseId != null ? courseNameById.get(courseId) : null,
                asString(properties.get(SearchableEntitySchema.Properties.TITLE)), asString(properties.get(SearchableEntitySchema.Properties.DESCRIPTION)),
                asString(properties.get(SearchableEntitySchema.Properties.SHORT_NAME)), buildLink(entityType, entityId, courseId, lectureId, examId, channelId),
                asIsoDate(properties.get(SearchableEntitySchema.Properties.RELEASE_DATE)), asIsoDate(properties.get(SearchableEntitySchema.Properties.START_DATE)),
                asIsoDate(properties.get(SearchableEntitySchema.Properties.DUE_DATE)), asIsoDate(properties.get(SearchableEntitySchema.Properties.END_DATE)),
                asIsoDate(properties.get(SearchableEntitySchema.Properties.EXAM_VISIBLE_DATE)), asIsoDate(properties.get(SearchableEntitySchema.Properties.EXAM_START_DATE)),
                asIsoDate(properties.get(SearchableEntitySchema.Properties.EXAM_END_DATE)), asDouble(properties.get(SearchableEntitySchema.Properties.MAX_POINTS)),
                asLong(properties.get(SearchableEntitySchema.Properties.QUIZ_DURATION)), asString(properties.get(SearchableEntitySchema.Properties.PROGRAMMING_LANGUAGE)),
                asString(properties.get(SearchableEntitySchema.Properties.EXERCISE_TYPE)), asString(properties.get(SearchableEntitySchema.Properties.UNIT_TYPE)),
                asString(properties.get(SearchableEntitySchema.Properties.FAQ_STATE)), asBoolean(properties.get(SearchableEntitySchema.Properties.CHANNEL_IS_PUBLIC)));
    }

    /**
     * Builds the Artemis-relative deep link for an entity, mirroring the client routes. Returns null
     * when the ids required for the route are missing.
     */
    @Nullable
    private static String buildLink(@Nullable String entityType, @Nullable Long entityId, @Nullable Long courseId, @Nullable Long lectureId, @Nullable Long examId,
            @Nullable Long channelId) {
        if (entityType == null) {
            return null;
        }
        return switch (entityType) {
            case SearchableEntitySchema.TypeValues.COURSE -> entityId != null ? "/courses/" + entityId : null;
            case SearchableEntitySchema.TypeValues.EXERCISE -> courseId != null && entityId != null ? "/courses/" + courseId + "/exercises/" + entityId : null;
            case SearchableEntitySchema.TypeValues.LECTURE -> courseId != null && entityId != null ? "/courses/" + courseId + "/lectures/" + entityId : null;
            case SearchableEntitySchema.TypeValues.LECTURE_UNIT -> courseId != null && lectureId != null ? "/courses/" + courseId + "/lectures/" + lectureId : null;
            case SearchableEntitySchema.TypeValues.EXAM ->
                courseId != null && (examId != null || entityId != null) ? "/courses/" + courseId + "/exams/" + (examId != null ? examId : entityId) : null;
            case SearchableEntitySchema.TypeValues.FAQ -> courseId != null ? "/courses/" + courseId + "/faq" : null;
            case SearchableEntitySchema.TypeValues.CHANNEL -> courseId != null && entityId != null ? "/courses/" + courseId + "/communication?conversationId=" + entityId : null;
            case SearchableEntitySchema.TypeValues.POST, SearchableEntitySchema.TypeValues.ANSWER_POST ->
                courseId != null && channelId != null ? "/courses/" + courseId + "/communication?conversationId=" + channelId : null;
            default -> null;
        };
    }

    @Nullable
    private static String asString(@Nullable Object value) {
        return value != null ? value.toString() : null;
    }

    @Nullable
    private static String asIsoDate(@Nullable Object value) {
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.toString();
        }
        return value != null ? value.toString() : null;
    }

    @Nullable
    private static Long asLong(@Nullable Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    @Nullable
    private static Double asDouble(@Nullable Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    @Nullable
    private static Boolean asBoolean(@Nullable Object value) {
        return value instanceof Boolean bool ? bool : null;
    }
}
