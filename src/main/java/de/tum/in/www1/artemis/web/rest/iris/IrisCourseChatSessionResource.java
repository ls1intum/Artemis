package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URISyntaxException;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
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
import de.tum.in.www1.artemis.service.iris.dto.IrisCombinedSettingsDTO;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

/**
 * REST controller for managing {@link IrisChatSession}.
 */
@Profile("iris")
@RestController
@RequestMapping("api/iris/")
public class IrisCourseChatSessionResource extends IrisCourseChatBasedSessionResource<Course, IrisCourseChatSession> {

    private final IrisCourseChatSessionRepository irisCourseChatSessionRepository;

    protected IrisCourseChatSessionResource(AuthorizationCheckService authCheckService, UserRepository userRepository, CourseRepository courseRepository,
            IrisSessionService irisSessionService, IrisSettingsService irisSettingsService, PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService,
            IrisCourseChatSessionRepository irisCourseChatSessionRepository) {
        super(authCheckService, userRepository, irisSessionService, irisSettingsService, pyrisHealthIndicator, irisRateLimitService, courseRepository);
        this.irisCourseChatSessionRepository = irisCourseChatSessionRepository;
    }

    /**
     * GET course/{courseId}/sessions/current: Retrieve the current iris session for the programming exercise.
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("course/{courseId}/sessions/current")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisCourseChatSession> getCurrentSession(@PathVariable Long courseId) {
        return super.getCurrentSession(courseId, IrisSubSettingsType.COURSE_CHAT, Role.STUDENT,
                (course, user) -> irisCourseChatSessionRepository.findNewestByCourseIdAndUserIdElseThrow(course.getId(), user.getId()));
    }

    /**
     * GET course/{courseId}/sessions: Retrieve all Iris Sessions for the programming exercise
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("course/{courseId}/sessions")
    @EnforceAtLeastStudent
    public ResponseEntity<List<IrisCourseChatSession>> getAllSessions(@PathVariable Long courseId) {
        return super.getAllSessions(courseId, IrisSubSettingsType.COURSE_CHAT, Role.STUDENT,
                (course, user) -> irisCourseChatSessionRepository.findByExerciseIdAndUserIdElseThrow(course.getId(), user.getId()));
    }

    /**
     * POST course/{courseId}/session: Create a new iris session for an course and user.
     * If there already exists an iris session for the exercise and user, a new one is created.
     * Note: The old session including messages is not deleted and can still be retrieved
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new iris session for the exercise
     */
    @PostMapping("course/{courseId}/sessions")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisCourseChatSession> createSessionForCourse(@PathVariable Long courseId) throws URISyntaxException {
        return super.createSessionForCourse(courseId, IrisSubSettingsType.COURSE_CHAT, Role.STUDENT, (course, user) -> {
            if (course instanceof Course c) {
                return irisCourseChatSessionRepository.save(new IrisCourseChatSession(c, user));
            }
            else {
                throw new ConflictException("Iris is only supported for programming exercises", "Iris", "irisProgrammingExercise");
            }
        });
    }

    /**
     * GET iris/sessions/{sessionId}/active: Retrieve if Iris is active for a session
     * This checks if the used model is healthy.
     *
     * @param sessionId id of the session
     * @return a status {@code 200 (Ok)} and with body true if Iris is active, false otherwise
     */
    @GetMapping("sessions/{sessionId}/active")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisHealthDTO> isIrisActive(@PathVariable Long sessionId) {
        var session = irisCourseChatSessionRepository.findByIdElseThrow(sessionId);
        return ResponseEntity.ok(super.isIrisActiveInternal(session.getCourse(), session, IrisCombinedSettingsDTO::irisChatSettings));
    }
}
