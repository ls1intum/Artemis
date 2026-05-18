package de.tum.cit.aet.artemis.iris.web;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.LimitRequestsPerMinute;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisAccessContextDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisSearchAskRequestDTO;

/**
 * REST controller for Iris lecture search.
 */
@Conditional(IrisEnabled.class)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisLectureSearchResource {

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    public IrisLectureSearchResource(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, UserRepository userRepository, CourseRepository courseRepository,
            AuthorizationCheckService authCheckService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * Resolves the course IDs the user can access, grouped by role.
     * Mirrors {@code GlobalSearchResource.groupCoursesByRole} so access rules stay in one place —
     * Artemis computes the sets, Pyris applies them as opaque Weaviate filters.
     *
     * @param user the authenticated user (must have groups loaded)
     * @return access context with role-based course ID sets
     */
    private PyrisAccessContextDTO buildAccessContext(User user) {
        // Admins see everything — null access context means Pyris applies no Weaviate filter.
        // Mirrors GlobalSearchResource.buildSearchableItemFilter which returns filter=null for admins.
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
        return new PyrisAccessContextDTO(allIds, editorIds, taIds, studentIds, staffIds);
    }

    /**
     * POST api/iris/lecture-search: Search for lecture units using Pyris.
     *
     * @param requestDTO the search request containing query and limit
     * @param principal  the authenticated user
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of search results
     */
    @PostMapping("lecture-search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<PyrisLectureSearchResultDTO>> search(@RequestBody @Valid PyrisLectureSearchRequestDTO requestDTO, Principal principal) {
        var user = userRepository.getUserWithGroupsAndAuthorities(principal.getName());
        var accessContext = buildAccessContext(user);
        return ResponseEntity.ok(pyrisConnectorService.searchLectures(requestDTO.query(), requestDTO.limit(), accessContext));
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
    public ResponseEntity<Void> ask(@RequestBody @Valid PyrisSearchAskRequestDTO requestDTO, Principal principal) {
        var user = userRepository.getUserWithGroupsAndAuthorities(principal.getName());
        user.hasOptedIntoLLMUsageElseThrow();
        var accessContext = buildAccessContext(user);
        pyrisJobService.addGlobalSearchAnswerJob(principal.getName(), requestDTO.runId().toString());
        // Note: do NOT remove the job on exception here. Transport-level failures are ambiguous —
        // Pyris may have received the request and already started the pipeline. Removing the token
        // would break WebSocket routing for any callbacks that arrive later.
        // Jobs expire automatically via the Hazelcast TTL (default 5 minutes).
        pyrisConnectorService.executeGlobalSearchIrisAnswer(requestDTO.query(), requestDTO.limit(), requestDTO.runId().toString(), user.getSelectedLLMUsage(), accessContext);
        return ResponseEntity.accepted().build();
    }
}
