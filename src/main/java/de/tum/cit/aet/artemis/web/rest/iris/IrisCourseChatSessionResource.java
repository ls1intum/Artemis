package de.tum.cit.aet.artemis.web.rest.iris;

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

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.connectors.pyris.PyrisHealthIndicator;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.repository.IrisCourseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.IrisSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

/**
 * REST controller for managing {@link IrisCourseChatSession}.
 */
@Profile("iris")
@RestController
@RequestMapping("api/iris/course-chat/")
public class IrisCourseChatSessionResource {

    protected final UserRepository userRepository;

    protected final IrisSessionService irisSessionService;

    protected final IrisSettingsService irisSettingsService;

    protected final PyrisHealthIndicator pyrisHealthIndicator;

    protected final IrisRateLimitService irisRateLimitService;

    protected final CourseRepository courseRepository;

    private final IrisCourseChatSessionRepository irisCourseChatSessionRepository;

    private final IrisCourseChatSessionService irisCourseChatSessionService;

    protected IrisCourseChatSessionResource(IrisCourseChatSessionRepository irisCourseChatSessionRepository, UserRepository userRepository, CourseRepository courseRepository,
            IrisSessionService irisSessionService, IrisSettingsService irisSettingsService, PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService,
            IrisCourseChatSessionService irisCourseChatSessionService) {
        this.irisCourseChatSessionRepository = irisCourseChatSessionRepository;
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
        this.irisRateLimitService = irisRateLimitService;
        this.courseRepository = courseRepository;
        this.irisCourseChatSessionService = irisCourseChatSessionService;
    }

    /**
     * GET course-chat/{courseId}/sessions/current: Retrieve the current iris session for the course.
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the course or {@code 404 (Not Found)} if no session exists
     */
    @PostMapping("{courseId}/sessions/current")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<IrisCourseChatSession> getCurrentSessionOrCreateIfNotExists(@PathVariable Long courseId) throws URISyntaxException {
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        user.hasAcceptedIrisElseThrow();

        var session = irisCourseChatSessionService.getCurrentSessionOrCreateIfNotExists(course, user, true);
        return ResponseEntity.ok(session);
    }

    /**
     * GET course-chat/{courseId}/sessions: Retrieve all Iris Sessions for the course
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the course or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("{courseId}/sessions")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<List<IrisCourseChatSession>> getAllSessions(@PathVariable Long courseId) {
        var course = courseRepository.findByIdElseThrow(courseId);

        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.CHAT, course);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        user.hasAcceptedIrisElseThrow();

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
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<IrisCourseChatSession> createSessionForCourse(@PathVariable Long courseId) throws URISyntaxException {
        var course = courseRepository.findByIdElseThrow(courseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        user.hasAcceptedIrisElseThrow();

        var session = irisCourseChatSessionService.createSession(course, user, false);
        var uriString = "/api/iris/sessions/" + session.getId();
        return ResponseEntity.created(new URI(uriString)).body(session);
    }
}
