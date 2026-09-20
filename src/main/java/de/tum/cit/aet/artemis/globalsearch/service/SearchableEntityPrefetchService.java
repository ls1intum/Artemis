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
     * @param user             the requesting user (with course roles loaded)
     * @param query            the search query
     * @param limit            the maximum number of candidates
     * @param courseIds        optional course ids to scope the candidates to (empty/null for unscoped)
     * @param excludeCourseIds course ids to hide regardless of {@code courseIds}; only needed for a caller with no
     *                             {@code courseIds} ceiling to narrow itself
     * @return the candidates, empty when the user has no accessible courses
     */
    public List<SearchableEntityCandidateDTO> prefetchCandidates(User user, String query, int limit, @Nullable List<Long> courseIds, List<Long> excludeCourseIds) {
        var filterResult = accessFilterService.buildSearchableItemFilter(user, courseIds, excludeCourseIds, PREFETCH_TYPES);
        if (!filterResult.hasAccess()) {
            return List.of();
        }
        List<Map<String, Object>> rawResults = searchableEntityWeaviateService.searchEntityCandidatesForAnswer(query, filterResult.filter(), limit);
        Map<Long, String> courseNameById = resolveCourseNames(rawResults, filterResult);
        List<SearchableEntityCandidateDTO> candidates = new ArrayList<>(rawResults.size());
        for (Map<String, Object> properties : rawResults) {
            candidates.add(toCandidate(properties, courseNameById, filterResult.editorCourseIds(), filterResult.staffCourseIds()));
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

    private static SearchableEntityCandidateDTO toCandidate(Map<String, Object> properties, Map<Long, String> courseNameById, @Nullable Set<Long> editorCourseIds,
            @Nullable Set<Long> staffCourseIds) {
        String entityType = asString(properties.get(SearchableEntitySchema.Properties.TYPE));
        Long entityId = asLong(properties.get(SearchableEntitySchema.Properties.ENTITY_ID));
        Long courseId = asLong(properties.get(SearchableEntitySchema.Properties.COURSE_ID));
        Long lectureId = asLong(properties.get(SearchableEntitySchema.Properties.LECTURE_ID));
        Long examId = asLong(properties.get(SearchableEntitySchema.Properties.EXAM_ID));
        Long channelId = asLong(properties.get(SearchableEntitySchema.Properties.CHANNEL_ID));
        Long postId = asLong(properties.get(SearchableEntitySchema.Properties.POST_ID));
        // null editor/staffCourseIds is the admin-with-no-course-scope fast path in the access filter
        // (FilterBuildResult carries no per-course map there): an admin has instructor-equivalent access
        // to every course, so treat it as "at least editor"/"at least staff" everywhere rather than NPE.
        // courseId is null for entity types with no course of their own (e.g. COURSE rows); Set.of(...)'s
        // immutable-set contains() throws on a null argument rather than returning false, so it is
        // checked first — the result is unused anyway, since buildLink only reads these for a role-aware
        // route, which always requires a non-null courseId.
        boolean isAtLeastEditor = courseId != null && (editorCourseIds == null || editorCourseIds.contains(courseId));
        boolean isAtLeastStaff = courseId != null && (staffCourseIds == null || staffCourseIds.contains(courseId));
        return new SearchableEntityCandidateDTO(entityType, entityId, courseId, courseId != null ? courseNameById.get(courseId) : null,
                asString(properties.get(SearchableEntitySchema.Properties.TITLE)), asString(properties.get(SearchableEntitySchema.Properties.DESCRIPTION)),
                asString(properties.get(SearchableEntitySchema.Properties.SHORT_NAME)),
                buildLink(entityType, entityId, courseId, lectureId, examId, channelId, postId, isAtLeastEditor, isAtLeastStaff),
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
     *
     * @param isAtLeastEditor whether the user is an editor/instructor in {@code courseId}
     * @param isAtLeastStaff  whether the user is at least a teaching assistant in {@code courseId} (editors satisfy
     *                            this too; check {@code isAtLeastEditor} first)
     */
    @Nullable
    private static String buildLink(@Nullable String entityType, @Nullable Long entityId, @Nullable Long courseId, @Nullable Long lectureId, @Nullable Long examId,
            @Nullable Long channelId, @Nullable Long postId, boolean isAtLeastEditor, boolean isAtLeastStaff) {
        if (entityType == null) {
            return null;
        }
        return switch (entityType) {
            case SearchableEntitySchema.TypeValues.COURSE -> entityId != null ? "/courses/" + entityId : null;
            case SearchableEntitySchema.TypeValues.EXERCISE -> {
                if (courseId == null || entityId == null) {
                    yield null;
                }
                // A regular (non-exam) exercise has no examId indexed; the palette itself sends every
                // role to the same student view there, so this case is unaffected by role.
                yield examId == null ? "/courses/" + courseId + "/exercises/" + entityId : buildExamExerciseLink(courseId, examId, entityId, isAtLeastEditor, isAtLeastStaff);
            }
            case SearchableEntitySchema.TypeValues.LECTURE -> courseId != null && entityId != null ? "/courses/" + courseId + "/lectures/" + entityId : null;
            case SearchableEntitySchema.TypeValues.LECTURE_UNIT -> courseId != null && lectureId != null ? "/courses/" + courseId + "/lectures/" + lectureId : null;
            case SearchableEntitySchema.TypeValues.EXAM -> {
                Long resolvedExamId = examId != null ? examId : entityId;
                yield courseId != null && resolvedExamId != null ? buildExamLink(courseId, resolvedExamId, isAtLeastEditor, isAtLeastStaff) : null;
            }
            case SearchableEntitySchema.TypeValues.FAQ -> courseId != null ? "/courses/" + courseId + "/faq" : null;
            case SearchableEntitySchema.TypeValues.CHANNEL -> courseId != null && entityId != null ? "/courses/" + courseId + "/communication?conversationId=" + entityId : null;
            // Mirrors the palette's own click handler (GlobalSearchNavigationViewComponent): a post is
            // focused by its own id, a reply additionally names its parent post so the client can open
            // the right thread. Without these, the citation opens the channel but not the cited message.
            case SearchableEntitySchema.TypeValues.POST ->
                courseId != null && channelId != null && entityId != null ? "/courses/" + courseId + "/communication?conversationId=" + channelId + "&focusPostId=" + entityId
                        : null;
            case SearchableEntitySchema.TypeValues.ANSWER_POST -> courseId != null && channelId != null && postId != null && entityId != null
                    ? "/courses/" + courseId + "/communication?conversationId=" + channelId + "&messageId=" + postId + "&focusReplyId=" + entityId
                    : null;
            default -> null;
        };
    }

    /**
     * Mirrors {@code GlobalSearchNavigationViewComponent.navigateToExam}: editors manage the exam, TAs assess it,
     * everyone else takes it.
     */
    private static String buildExamLink(long courseId, long examId, boolean isAtLeastEditor, boolean isAtLeastStaff) {
        if (isAtLeastEditor) {
            return "/course-management/" + courseId + "/exams/" + examId;
        }
        if (isAtLeastStaff) {
            return "/course-management/" + courseId + "/exams/" + examId + "/assessment-dashboard";
        }
        return "/courses/" + courseId + "/exams/" + examId;
    }

    /**
     * Mirrors {@code GlobalSearchNavigationViewComponent.navigateToExercise}'s exam-exercise branch. The exact
     * exercise-group and type-prefixed route (used when those are known client-side) needs an exercise-group id
     * this schema does not index, so an editor lands on the same exercise-groups list page the palette itself falls
     * back to when it cannot resolve that route either.
     */
    private static String buildExamExerciseLink(long courseId, long examId, long exerciseId, boolean isAtLeastEditor, boolean isAtLeastStaff) {
        if (isAtLeastEditor) {
            return "/course-management/" + courseId + "/exams/" + examId + "/exercise-groups";
        }
        if (isAtLeastStaff) {
            return "/course-management/" + courseId + "/exams/" + examId + "/assessment-dashboard/" + exerciseId;
        }
        return "/courses/" + courseId + "/exams/" + examId;
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
