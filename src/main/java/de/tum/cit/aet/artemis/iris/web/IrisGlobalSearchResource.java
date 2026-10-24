package de.tum.cit.aet.artemis.iris.web;

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
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;

/**
 * REST controller for Iris global search.
 * Exposes two endpoints:
 * <ul>
 * <li>{@code POST api/iris/lecture-search} — synchronous semantic lecture-unit search via Pyris.</li>
 * <li>{@code POST api/iris/search-answer} — the inline Iris answer, which is refused in this release, see {@link #ask()}.</li>
 * </ul>
 */
@Conditional(IrisEnabled.class)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisGlobalSearchResource {

    private static final String ENTITY_NAME = "iris";

    private final PyrisConnectorService pyrisConnectorService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    public IrisGlobalSearchResource(PyrisConnectorService pyrisConnectorService, UserRepository userRepository, CourseRepository courseRepository,
            AuthorizationCheckService authCheckService) {
        this.pyrisConnectorService = pyrisConnectorService;
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
     * POST api/iris/search-answer: The inline Iris answer of the global search is not available in this release.
     * <p>
     * The request is refused with {@code 403 (Forbidden)} and {@code skipAlert}, so the client shows the search results without an answer card
     * and without an error alert. The answer needs a course scope that the request to Pyris does not carry in this release.
     *
     * @return never; always refused
     */
    @PostMapping("search-answer")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> ask() {
        throw new AccessForbiddenAlertException(ErrorConstants.DEFAULT_TYPE, "The Iris answer of the global search is not available in this release", ENTITY_NAME,
                "iris.searchAnswerUnavailable", true);
    }
}
