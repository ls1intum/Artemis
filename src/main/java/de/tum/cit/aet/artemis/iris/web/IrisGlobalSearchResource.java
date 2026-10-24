package de.tum.cit.aet.artemis.iris.web;

import java.security.Principal;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.LimitRequestsPerMinute;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.GlobalSearchAskRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;

/**
 * REST controller for Iris global search.
 * Exposes two endpoints:
 * <ul>
 * <li>{@code POST api/iris/lecture-search} — synchronous semantic lecture-unit search via Pyris.</li>
 * <li>{@code POST api/iris/search-answer} — asynchronous Iris inline-answer pipeline; results are pushed to the client via WebSocket.</li>
 * </ul>
 */
@Conditional(IrisEnabled.class)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisGlobalSearchResource {

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    public IrisGlobalSearchResource(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, UserRepository userRepository, CourseRepository courseRepository,
            AuthorizationCheckService authCheckService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * POST api/iris/lecture-search: Search for lecture units using Pyris.
     * <p>
     * The search covers the courses the caller can access. The optional course filter narrows it further, and a requested course the caller
     * cannot access is dropped before the request is sent to Pyris.
     *
     * @param requestDTO the search request containing query, limit, and optional courseIds filter
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of search results
     */
    @PostMapping("lecture-search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<PyrisLectureSearchResultDTO>> search(@RequestBody @Valid PyrisLectureSearchRequestDTO requestDTO) {
        var user = userRepository.getUserWithAuthorities();
        var courseIds = restrictToAccessibleCourses(user, requestDTO.courseIds());
        if (courseIds != null && courseIds.isEmpty()) {
            // An empty list is omitted on the wire and read by Pyris as "all courses", so nothing to search is answered here.
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(pyrisConnectorService.searchLectures(requestDTO.query(), requestDTO.limit(), courseIds));
    }

    /**
     * Limits the requested courses to the courses the caller can access.
     * <p>
     * An unscoped request is narrowed to every course the caller can access. Administrators can access every course, so their request is
     * forwarded as sent and stays unscoped when they name no course.
     *
     * @param user               the caller, loaded with authorities
     * @param requestedCourseIds the course ids the client asked for, {@code null} or empty for an unscoped search
     * @return the course ids to search, {@code null} for an unscoped search of an administrator, and an empty list if there is nothing to search
     */
    @Nullable
    private List<Long> restrictToAccessibleCourses(User user, @Nullable List<Long> requestedCourseIds) {
        boolean isUnscoped = requestedCourseIds == null || requestedCourseIds.isEmpty();
        if (authCheckService.isAdmin(user)) {
            return isUnscoped ? null : requestedCourseIds;
        }
        var accessibleCourseIds = courseRepository.findAllAccessibleCoursesForUser(user.getId(), false).stream().map(Course::getId).toList();
        if (isUnscoped) {
            return accessibleCourseIds;
        }
        var accessibleCourseIdSet = Set.copyOf(accessibleCourseIds);
        return requestedCourseIds.stream().filter(accessibleCourseIdSet::contains).toList();
    }

    /**
     * POST api/iris/search-answer: Ask Iris to answer a question using course content (async).
     * Pyris classifies the query and sends webhook callbacks; results are pushed to the client via WebSocket.
     *
     * @param requestDTO the request containing the query and result limit
     * @param principal  the authenticated user (used to route the WebSocket response)
     * @return the {@link ResponseEntity} with status {@code 202 (Accepted)}
     */
    @PostMapping("search-answer")
    @EnforceAtLeastStudent
    @LimitRequestsPerMinute(type = RateLimitType.AI_SEARCH_PIPELINE)
    public ResponseEntity<Void> ask(@RequestBody @Valid GlobalSearchAskRequestDTO requestDTO, Principal principal) {
        var user = userRepository.findOneByLogin(principal.getName()).orElseThrow(() -> new EntityNotFoundException("User", principal.getName()));
        user.hasOptedIntoLLMUsageElseThrow();
        pyrisJobService.addGlobalSearchAnswerJob(principal.getName(), requestDTO.runId().toString());
        // Note: do NOT remove the job on exception here. Transport-level failures are ambiguous —
        // Pyris may have received the request and already started the pipeline. Removing the token
        // would break WebSocket routing for any callbacks that arrive later.
        // Jobs expire automatically via the Hazelcast TTL (default 5 minutes).
        pyrisConnectorService.executeGlobalSearchIrisAnswer(requestDTO.query(), requestDTO.limit(), requestDTO.runId().toString(), user.getSelectedLLMUsage());
        return ResponseEntity.accepted().build();
    }
}
