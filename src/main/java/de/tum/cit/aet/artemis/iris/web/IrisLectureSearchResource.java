package de.tum.cit.aet.artemis.iris.web;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.LimitRequestsPerMinute;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.service.GlobalSearchService;
import de.tum.cit.aet.artemis.iris.api.IrisLectureSearchApi;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisAccessContextDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisSearchAskRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisSearchableEntityDTO;

/**
 * REST controller for Iris lecture search and global search answer pipeline.
 */
@Conditional(IrisEnabled.class)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisLectureSearchResource {

    /** Number of searchable entities pre-fetched from Artemis Weaviate for the Iris answer pipeline. */
    private static final int ENTITY_PREFETCH_LIMIT = 15;

    private final IrisLectureSearchApi irisLectureSearchApi;

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    private final Optional<GlobalSearchService> globalSearchService;

    public IrisLectureSearchResource(IrisLectureSearchApi irisLectureSearchApi, PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService,
            UserRepository userRepository, CourseRepository courseRepository, AuthorizationCheckService authCheckService, Optional<GlobalSearchService> globalSearchService) {
        this.irisLectureSearchApi = irisLectureSearchApi;
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.globalSearchService = globalSearchService;
    }

    /**
     * Resolves the course IDs the user can access, grouped by role.
     * Used for the lecture-search access context sent to Pyris.
     *
     * @param user the authenticated user (must have groups loaded)
     * @return access context with role-based course ID sets, or {@code null} for admins
     */
    private PyrisAccessContextDTO buildAccessContext(User user) {
        if (authCheckService.isAdmin(user)) {
            return null;
        }
        var courses = courseRepository.findAllAccessibleCoursesForUser(user.getGroups(), false);
        var editorIds = new java.util.ArrayList<Long>();
        var taIds = new java.util.ArrayList<Long>();
        var studentIds = new java.util.ArrayList<Long>();
        for (Course course : courses) {
            if (authCheckService.isAtLeastEditorInCourse(course, user)) {
                editorIds.add(course.getId());
            }
            else if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
                taIds.add(course.getId());
            }
            else {
                studentIds.add(course.getId());
            }
        }
        var staffIds = new java.util.ArrayList<Long>(editorIds.size() + taIds.size());
        staffIds.addAll(editorIds);
        staffIds.addAll(taIds);
        var allIds = courses.stream().map(Course::getId).toList();
        return new PyrisAccessContextDTO(allIds, editorIds, taIds, studentIds, staffIds, ZonedDateTime.now());
    }

    /**
     * Pre-fetches exercise, FAQ, exam, and channel entities from Weaviate using the user's full
     * access context. Returns an empty list when Weaviate is not enabled.
     */
    private List<PyrisSearchableEntityDTO> prefetchEntities(String query, User user) {
        if (globalSearchService.isEmpty()) {
            return List.of();
        }
        var rawResults = globalSearchService.get().searchEntitiesForIrisPipeline(query, user, ENTITY_PREFETCH_LIMIT);
        List<PyrisSearchableEntityDTO> entities = new ArrayList<>(rawResults.size());
        for (var props : rawResults) {
            PyrisSearchableEntityDTO dto = PyrisSearchableEntityDTO.fromProperties(props);
            if (dto != null) {
                entities.add(dto);
            }
        }
        return entities;
    }

    /**
     * POST api/iris/lecture-search: Search for lecture units using Pyris.
     */
    @PostMapping("lecture-search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<PyrisLectureSearchResultDTO>> search(@RequestBody @Valid PyrisLectureSearchRequestDTO requestDTO, Principal principal) {
        var user = userRepository.getUserWithGroupsAndAuthorities(principal.getName());
        var accessContext = buildAccessContext(user);
        return ResponseEntity.ok(irisLectureSearchApi.searchLecturesByAccessContext(requestDTO.query(), requestDTO.limit(), requestDTO.courseIds(), accessContext));
    }

    /**
     * POST api/iris/search-answer: Ask Iris to answer a question using course content (async).
     * Artemis pre-fetches searchable entities from Weaviate and includes them in the Pyris request
     * so Pyris never needs to query the SearchableEntities collection directly.
     * Pyris sends webhook callbacks; results are pushed to the client via WebSocket.
     */
    @PostMapping("search-answer")
    @EnforceAtLeastStudent
    @LimitRequestsPerMinute(type = RateLimitType.AI_SEARCH_PIPELINE)
    public ResponseEntity<Void> ask(@RequestBody @Valid PyrisSearchAskRequestDTO requestDTO, Principal principal) {
        var user = userRepository.getUserWithGroupsAndAuthorities(principal.getName());
        user.hasOptedIntoLLMUsageElseThrow();
        // Register the job before returning so WebSocket callbacks can be routed immediately.
        pyrisJobService.addGlobalSearchAnswerJob(principal.getName(), requestDTO.runId().toString());
        // Run the Weaviate pre-fetch and Pyris call asynchronously so the 202 is returned before
        // the prefetch query hits Weaviate, preventing contention with concurrent /api/search requests.
        CompletableFuture.runAsync(() -> {
            var accessContext = buildAccessContext(user);
            var prefetchedEntities = prefetchEntities(requestDTO.query(), user);
            // Note: do NOT remove the job on exception here. Transport-level failures are ambiguous —
            // Pyris may have received the request and already started the pipeline. Removing the token
            // would break WebSocket routing for any callbacks that arrive later.
            // Jobs expire automatically via the Hazelcast TTL (default 5 minutes).
            pyrisConnectorService.executeGlobalSearchIrisAnswer(requestDTO.query(), requestDTO.limit(), requestDTO.runId().toString(), user.getSelectedLLMUsage(), accessContext,
                    prefetchedEntities);
        });
        return ResponseEntity.accepted().build();
    }
}
