package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.iris.session.IrisCourseChatSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisCourseChatSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisHealthIndicator;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;

/**
 * REST controller for managing {@link IrisCourseChatSession}.
 */
@Profile("iris")
@RestController
@RequestMapping("api/iris/course-chat/")
public class IrisCourseChatSessionResource {

    protected final AuthorizationCheckService authCheckService;

    protected final UserRepository userRepository;

    protected final IrisSessionService irisSessionService;

    protected final IrisSettingsService irisSettingsService;

    protected final PyrisHealthIndicator pyrisHealthIndicator;

    protected final IrisRateLimitService irisRateLimitService;

    protected final CourseRepository courseRepository;

    private final IrisCourseChatSessionRepository irisCourseChatSessionRepository;

    protected IrisCourseChatSessionResource(AuthorizationCheckService authCheckService, IrisCourseChatSessionRepository irisCourseChatSessionRepository,
            UserRepository userRepository, CourseRepository courseRepository, IrisSessionService irisSessionService, IrisSettingsService irisSettingsService,
            PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService) {
        this.irisCourseChatSessionRepository = irisCourseChatSessionRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
        this.irisRateLimitService = irisRateLimitService;
        this.courseRepository = courseRepository;
    }

    /**
     * GET course-chat/{courseId}/sessions/current: Retrieve the current iris session for the course.
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the course or {@code 404 (Not Found)} if no session exists
     */
    @PostMapping("{courseId}/sessions/current")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisCourseChatSession> getCurrentSessionOrCreateIfNotExists(@PathVariable Long courseId) throws URISyntaxException {
        var course = courseRepository.findByIdElseThrow(courseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, course);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        var sessionOptional = irisCourseChatSessionRepository.findLatestByCourseIdAndUserIdWithMessages(course.getId(), user.getId());
        if (sessionOptional.isPresent()) {
            var session = sessionOptional.get();
            irisSessionService.checkHasAccessToIrisSession(session, user);
            return ResponseEntity.ok(session);
        }

        return createSessionForCourse(courseId);
    }

    /**
     * GET course-chat/{courseId}/sessions: Retrieve all Iris Sessions for the course
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the course or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("{courseId}/sessions")
    @EnforceAtLeastStudent
    public ResponseEntity<List<IrisCourseChatSession>> getAllSessions(@PathVariable Long courseId) {
        var course = courseRepository.findByIdElseThrow(courseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, course);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        var sessions = irisCourseChatSessionRepository.findByExerciseIdAndUserIdElseThrow(course.getId(), user.getId());
        sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
        return ResponseEntity.ok(sessions);
    }

    /**
     * POST course-chat/{courseId}/session: Create a new iris session for a course and user.
     * If there already exists an iris session for the course and user, a new one is created.
     * Note: The old session including messages is not deleted and can still be retrieved
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new iris session for the course
     */
    @PostMapping("{courseId}/sessions")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisCourseChatSession> createSessionForCourse(@PathVariable Long courseId) throws URISyntaxException {
        var course = courseRepository.findByIdElseThrow(courseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, course);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        var session = irisCourseChatSessionRepository.save(new IrisCourseChatSession(course, user));
        var uriString = "/api/iris/sessions/" + session.getId();

        return ResponseEntity.created(new URI(uriString)).body(session);
    }
}
