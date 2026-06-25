package de.tum.cit.aet.artemis.globalsearch.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.GlobalSearchResultDTO;
import de.tum.cit.aet.artemis.globalsearch.service.GlobalSearchService;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST controller for the unified global search endpoint backed by Weaviate.
 * <p>
 * Delegates access-filter building to {@link GlobalSearchService} and issues exactly one
 * Weaviate request per invocation via {@link SearchableEntityWeaviateService}.
 */
@Lazy
@Conditional(WeaviateEnabled.class)
@RestController
@RequestMapping("api/")
@Tag(name = "Global Search Resource", description = "Weaviate-based semantic search across courses, exercises, lectures, lecture units, exams, FAQs, and public communication channels including their messages and replies")
public class GlobalSearchResource {

    private static final Logger log = LoggerFactory.getLogger(GlobalSearchResource.class);

    private static final Set<String> VALID_TYPES = Set.of(SearchableEntitySchema.TypeValues.EXERCISE, SearchableEntitySchema.TypeValues.LECTURE,
            SearchableEntitySchema.TypeValues.LECTURE_UNIT, SearchableEntitySchema.TypeValues.EXAM, SearchableEntitySchema.TypeValues.FAQ,
            SearchableEntitySchema.TypeValues.CHANNEL, SearchableEntitySchema.TypeValues.COURSE, SearchableEntitySchema.TypeValues.POST,
            SearchableEntitySchema.TypeValues.ANSWER_POST);

    private final GlobalSearchService globalSearchService;

    private final SearchableEntityWeaviateService searchableEntityWeaviateService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    private final ChannelRepository channelRepository;

    public GlobalSearchResource(GlobalSearchService globalSearchService, SearchableEntityWeaviateService searchableEntityWeaviateService, UserRepository userRepository,
            CourseRepository courseRepository, ExerciseRepository exerciseRepository, ChannelRepository channelRepository) {
        this.globalSearchService = globalSearchService;
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
        this.channelRepository = channelRepository;
    }

    /**
     * GET /api/search : unified global search across all indexable entity types.
     *
     * @param query    the search query (may be empty to browse recent items)
     * @param types    optional comma-separated list of types to include or {@code all} (default)
     * @param courseId optional course id to scope the search to a single course
     * @param limit    maximum number of results (default 10, max 25)
     * @return status 200 with a list of unified search results
     */
    @GetMapping("search")
    @EnforceAtLeastStudent
    @Operation(summary = "Perform a unified semantic search across entity types", description = """
            Searches across multiple entity types (exercises, lectures, lecture units, exams, FAQs, channels, courses, posts, answer posts)
            with a consistent response format. When courseId is not specified, the search is performed
            globally across all courses the authenticated user has access to. Per-type access rules are
            enforced server-side via compound Weaviate filters.""")
    @ApiResponse(responseCode = "200", description = "Search results matching the query")
    @ApiResponse(responseCode = "400", description = "Unsupported entity type requested")
    public ResponseEntity<List<GlobalSearchResultDTO>> globalSearch(@RequestParam("q") @Parameter(description = "Search query; can be empty to retrieve recent items") String query,
            @RequestParam(value = "types", required = false) @Parameter(description = "Comma-separated entity type filter or 'all'; default 'all'") String types,
            @RequestParam(value = "courseId", required = false) @Parameter(description = "Course ID to restrict the search to a single course") Long courseId,
            @RequestParam(value = "limit", defaultValue = "10") @Parameter(description = "Maximum number of results (1–25, default 10)") int limit) {
        log.debug("REST request for global search with query: '{}', types: {}, courseId: {}, limit: {}", query, types, courseId, limit);

        Set<String> requestedTypes = parseTypes(types);
        if (requestedTypes == null) {
            return ResponseEntity.badRequest().build();
        }

        int effectiveLimit = Math.clamp(limit, 1, 25);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        GlobalSearchService.FilterBuildResult filterResult = globalSearchService.buildFilter(user, courseId, requestedTypes);
        if (!filterResult.hasAccess()) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> rawResults = searchableEntityWeaviateService.searchSearchableEntities(query, filterResult.filter(), effectiveLimit);

        Map<Long, Course> coursesById;
        Set<Long> staffCourseIds;
        Set<Long> editorCourseIds;
        if (filterResult.accessibleCoursesById() != null) {
            coursesById = filterResult.accessibleCoursesById();
            staffCourseIds = filterResult.staffCourseIds();
            editorCourseIds = filterResult.editorCourseIds();
        }
        else {
            coursesById = resolveCoursesById(rawResults);
            staffCourseIds = coursesById.keySet();
            editorCourseIds = coursesById.keySet();
        }
        Map<Long, String> courseNameById = new HashMap<>();
        coursesById.forEach((id, course) -> courseNameById.put(id, course.getTitle()));

        Map<Long, Long> exerciseGroupIdByExerciseId = resolveExerciseGroupIds(rawResults);
        Map<Long, String> channelNameById = resolveChannelNames(rawResults);
        List<GlobalSearchResultDTO> resultDTOs = new ArrayList<>();
        for (Map<String, Object> properties : rawResults) {
            GlobalSearchResultDTO dto = GlobalSearchResultDTO.fromSearchableItemProperties(properties, courseNameById, exerciseGroupIdByExerciseId, staffCourseIds, editorCourseIds,
                    channelNameById);
            if (dto != null) {
                resultDTOs.add(dto);
            }
        }
        return ResponseEntity.ok(resultDTOs);
    }

    private static Set<String> parseTypes(String types) {
        if (types == null || types.isBlank() || "all".equalsIgnoreCase(types.trim())) {
            return new LinkedHashSet<>(VALID_TYPES);
        }
        Set<String> result = new LinkedHashSet<>();
        for (String token : types.split(",")) {
            String normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            if (!VALID_TYPES.contains(normalized)) {
                return null;
            }
            result.add(normalized);
        }
        return result.isEmpty() ? null : result;
    }

    private Map<Long, Course> resolveCoursesById(List<Map<String, Object>> rawResults) {
        Set<Long> courseIds = new HashSet<>();
        for (Map<String, Object> properties : rawResults) {
            Object raw = properties.get(SearchableEntitySchema.Properties.COURSE_ID);
            if (raw instanceof Number number) {
                courseIds.add(number.longValue());
            }
        }
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Course> result = new HashMap<>();
        courseRepository.findAllById(courseIds).forEach(course -> result.put(course.getId(), course));
        return result;
    }

    private Map<Long, Long> resolveExerciseGroupIds(List<Map<String, Object>> rawResults) {
        Set<Long> examExerciseIds = new HashSet<>();
        for (Map<String, Object> properties : rawResults) {
            Object type = properties.get(SearchableEntitySchema.Properties.TYPE);
            if (!SearchableEntitySchema.TypeValues.EXERCISE.equals(type)) {
                continue;
            }
            Object examId = properties.get(SearchableEntitySchema.Properties.EXAM_ID);
            if (examId == null) {
                continue;
            }
            Object rawId = properties.get(SearchableEntitySchema.Properties.ENTITY_ID);
            if (rawId instanceof Number number) {
                examExerciseIds.add(number.longValue());
            }
        }
        if (examExerciseIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (var dto : exerciseRepository.findExerciseAndGroupIdsByExerciseIds(examExerciseIds)) {
            result.put(dto.exerciseId(), dto.exerciseGroupId());
        }
        return result;
    }

    private Map<Long, String> resolveChannelNames(List<Map<String, Object>> rawResults) {
        Set<Long> channelIds = new HashSet<>();
        for (Map<String, Object> properties : rawResults) {
            Object type = properties.get(SearchableEntitySchema.Properties.TYPE);
            if (!SearchableEntitySchema.TypeValues.POST.equals(type) && !SearchableEntitySchema.TypeValues.ANSWER_POST.equals(type)) {
                continue;
            }
            Object rawId = properties.get(SearchableEntitySchema.Properties.CHANNEL_ID);
            if (rawId instanceof Number number) {
                channelIds.add(number.longValue());
            }
        }
        if (channelIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        channelRepository.findAllById(channelIds).forEach(channel -> result.put(channel.getId(), channel.getName()));
        return result;
    }
}
