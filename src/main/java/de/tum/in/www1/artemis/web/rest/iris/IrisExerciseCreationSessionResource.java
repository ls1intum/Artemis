package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.iris.session.IrisExerciseCreationSession;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisExerciseCreationSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisHealthIndicator;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisStatusDTO;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;
import de.tum.in.www1.artemis.service.iris.session.IrisExerciseCreationSessionService;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenAlertException;

/**
 * REST controller for managing {@link IrisExerciseCreationSession}.
 */
@RestController
@Profile("iris")
@RequestMapping("api/iris/")
public class IrisExerciseCreationSessionResource {

    private final AuthorizationCheckService authCheckService;

    private final CourseRepository courseRepository;

    private final IrisExerciseCreationSessionRepository irisExerciseCreationSessionRepository;

    private final UserRepository userRepository;

    private final IrisExerciseCreationSessionService irisSessionService;

    private final IrisSettingsService irisSettingsService;

    private final IrisHealthIndicator irisHealthIndicator;

    public IrisExerciseCreationSessionResource(AuthorizationCheckService authCheckService, CourseRepository courseRepository,
            IrisExerciseCreationSessionRepository irisExerciseCreationSessionRepository, UserRepository userRepository, IrisExerciseCreationSessionService irisSessionService,
            IrisSettingsService irisSettingsService, IrisHealthIndicator irisHealthIndicator, IrisRateLimitService irisRateLimitService) {
        this.courseRepository = courseRepository;
        this.irisExerciseCreationSessionRepository = irisExerciseCreationSessionRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.irisHealthIndicator = irisHealthIndicator;
    }

    /**
     * GET courses/{courseId}/sessions/current: Retrieve the current iris exercise creation session for the course.
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris exercise creation session for the course or {@code 404 (Not Found)} if no
     *         session exists
     */
    @GetMapping("courses/{courseId}/exercise-creation-sessions/current")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisExerciseCreationSession> getCurrentSession(@PathVariable Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!irisSettingsService.getCombinedIrisSettingsFor(course, false).irisCodeEditorSettings().isEnabled()) {
            throw new AccessForbiddenAlertException("The Iris code editor feature is disabled for this course.", "Iris", "iris.code_editor Disabled");
        }
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        var session = irisExerciseCreationSessionRepository.findNewestByExerciseIdAndUserIdElseThrow(course.getId(), user.getId());
        irisSessionService.checkHasAccessToIrisSession(session, user);
        return ResponseEntity.ok(session);
    }

    /**
     * GET courses/{courseId}/sessions: Retrieve all IrisExerciseCreationSessions for the course.
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the course or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("courses/{courseId}/exercise-creation-sessions")
    @EnforceAtLeastEditor
    public ResponseEntity<List<IrisExerciseCreationSession>> getAllSessions(@PathVariable Long courseId) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!irisSettingsService.getCombinedIrisSettingsFor(course, false).irisCodeEditorSettings().isEnabled()) {
            throw new AccessForbiddenAlertException("The Iris code editor feature is disabled for this course.", "Iris", "iris.code_editor Disabled");
        }
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        var sessions = irisExerciseCreationSessionRepository.findByCourseIdAndUserIdElseThrow(course.getId(), user.getId());
        sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
        return ResponseEntity.ok(sessions);
    }

    /**
     * POST courses/{courseId}/exercise-creation-sessions: Create a new exercise creation session for a course and user.
     * If there already exists an iris session for the course and user, a new one is created.
     * Note: The old session including messages is not deleted and can still be retrieved
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new iris session for the course
     */
    @PostMapping("courses/{courseId}/exercise-creation-sessions")
    @EnforceAtLeastEditor
    public ResponseEntity<IrisExerciseCreationSession> createSessionForCourse(@PathVariable Long courseId) throws URISyntaxException {
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!irisSettingsService.getCombinedIrisSettingsFor(course, false).irisCodeEditorSettings().isEnabled()) {
            throw new AccessForbiddenAlertException("The Iris code editor feature is disabled for this course.", "Iris", "iris.code_editor Disabled");
        }
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        var session = irisSessionService.createSession(course, user);

        var uriString = "/api/iris/exercise-creation-sessions/" + session.getId();
        return ResponseEntity.created(new URI(uriString)).body(session);
    }

    /**
     * GET exercise-creations-sessions/{sessionId}/active: Retrieve if Iris is active for a session
     * This checks if the used model is healthy.
     *
     * @param sessionId id of the session
     * @return a status {@code 200 (Ok)} and with body true if Iris is active, false otherwise
     */
    @GetMapping("/exercise-creations-sessions/{sessionId}/active")
    @EnforceAtLeastStudent
    public ResponseEntity<Boolean> isIrisActive(@PathVariable Long sessionId) {
        var session = irisExerciseCreationSessionRepository.findByIdElseThrow(sessionId);
        var user = userRepository.getUser();
        irisSessionService.checkHasAccessToIrisSession(session, user);
        irisSessionService.checkIsIrisActivated(session);
        var settings = irisSettingsService.getCombinedIrisSettingsFor(session.getCourse(), false);
        var health = irisHealthIndicator.health();
        IrisStatusDTO[] modelStatuses = (IrisStatusDTO[]) health.getDetails().get("modelStatuses");
        var specificModelStatus = false;
        if (modelStatuses != null) {
            specificModelStatus = Arrays.stream(modelStatuses).filter(x -> x.model().equals(settings.irisCodeEditorSettings().getPreferredModel()))
                    .anyMatch(x -> x.status() == IrisStatusDTO.ModelStatus.UP);
        }
        return ResponseEntity.ok(specificModelStatus);
    }
}
