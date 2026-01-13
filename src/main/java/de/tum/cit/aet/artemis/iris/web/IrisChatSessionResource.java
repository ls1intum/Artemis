package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.IrisSessionService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisHealthIndicator;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

/**
 * REST controller for managing {@link IrisChatSession}.
 */
@Profile(PROFILE_IRIS)
@Lazy
@RestController
@RequestMapping("api/iris/chat-history/")
public class IrisChatSessionResource {

    protected final UserRepository userRepository;

    protected final IrisSessionService irisSessionService;

    protected final IrisSettingsService irisSettingsService;

    protected final PyrisHealthIndicator pyrisHealthIndicator;

    protected final IrisRateLimitService irisRateLimitService;

    protected final CourseRepository courseRepository;

    private final IrisSessionRepository irisSessionRepository;

    protected IrisChatSessionResource(UserRepository userRepository, CourseRepository courseRepository, IrisSessionService irisSessionService,
            IrisSettingsService irisSettingsService, PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService,
            IrisSessionRepository irisSessionRepository) {
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
        this.irisRateLimitService = irisRateLimitService;
        this.courseRepository = courseRepository;
        this.irisSessionRepository = irisSessionRepository;
    }

    /**
     * GET chat-history/{courseId}/session/{id}: Retrieve an Iris Session for a id
     *
     * @param courseId  of the course
     * @param sessionId of the session
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the iris sessions for the id or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("{courseId}/session/{sessionId}")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<IrisChatSession> getSessionsForSessionId(@PathVariable Long courseId, @PathVariable Long sessionId) {
        IrisSession irisSession = irisSessionRepository.findByIdWithMessagesAndContents(sessionId);

        if (irisSession == null) {
            throw new EntityNotFoundException("Iris session with id " + sessionId + " not found");
        }

        irisSessionService.checkHasAccessToIrisSession(irisSession, null);

        boolean enabled = irisSettingsService.isEnabledForCourse(courseId);

        if (enabled) {
            return ResponseEntity.ok((IrisChatSession) irisSession);
        }
        throw new AccessForbiddenAlertException("This Iris chat Type is disabled in the course.", "iris", "iris.disabled");
    }

    /**
     * GET chat-history/{courseId}/sessions: Retrieve all Iris Sessions for the course and the current user.
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the course or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("{courseId}/sessions")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<List<IrisChatSessionDTO>> getAllSessionsForCourse(@PathVariable Long courseId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseRepository.findById(courseId).orElseThrow();
        if (user.hasAcceptedExternalLLMUsage()) {
            List<IrisChatSessionDTO> irisSessionDTOs = irisSessionService.getIrisSessionsByCourseAndUserId(course, user.getId());
            return ResponseEntity.ok(irisSessionDTOs);
        }
        else {
            return ResponseEntity.ok(List.of());
        }
    }
}
