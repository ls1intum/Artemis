package de.tum.cit.aet.artemis.iris.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.CustomAuditEventRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionCountDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisCitationService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.IrisSessionService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisHealthIndicator;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

/**
 * REST controller for managing {@link IrisChatSession}.
 */
@Conditional(IrisEnabled.class)
@Lazy
@RestController
@RequestMapping("api/iris/chat-history/")
public class IrisChatSessionResource {

    private static final Logger log = LoggerFactory.getLogger(IrisChatSessionResource.class);

    protected final UserRepository userRepository;

    protected final IrisSessionService irisSessionService;

    protected final IrisSettingsService irisSettingsService;

    protected final PyrisHealthIndicator pyrisHealthIndicator;

    protected final IrisRateLimitService irisRateLimitService;

    protected final CourseRepository courseRepository;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisCitationService irisCitationService;

    private final IrisChatSessionRepository irisChatSessionRepository;

    private final CustomAuditEventRepository auditEventRepository;

    protected IrisChatSessionResource(UserRepository userRepository, CourseRepository courseRepository, IrisSessionService irisSessionService,
            IrisSettingsService irisSettingsService, PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService,
            IrisSessionRepository irisSessionRepository, IrisCitationService irisCitationService, IrisChatSessionRepository irisChatSessionRepository,
            CustomAuditEventRepository auditEventRepository) {
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
        this.irisRateLimitService = irisRateLimitService;
        this.courseRepository = courseRepository;
        this.irisSessionRepository = irisSessionRepository;
        this.irisCitationService = irisCitationService;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.auditEventRepository = auditEventRepository;
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
            irisSession.setCitationInfo(irisCitationService.resolveCitationInfoFromMessages(irisSession.getMessages()));
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
        if (user.hasOptedIntoLLMUsage()) {
            List<IrisChatSessionDTO> irisSessionDTOs = irisSessionService.getIrisSessionsByCourseAndUserId(course, user.getId());
            return ResponseEntity.ok(irisSessionDTOs);
        }
        else {
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * GET /api/iris/chat-history/sessions/count : Get the number of sessions and messages for the current user.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and body containing session and message counts
     */
    @GetMapping("sessions/count")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisChatSessionCountDTO> getSessionAndMessageCount() {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        long sessionCount = irisChatSessionRepository.countByUserId(user.getId());
        long messageCount = irisChatSessionRepository.countMessagesByUserId(user.getId());
        return ResponseEntity.ok(new IrisChatSessionCountDTO(sessionCount, messageCount));
    }

    /**
     * DELETE /api/iris/chat-history/sessions : Delete all Iris chat sessions for the current user.
     * Messages and their content are removed via cascade.
     *
     * @return the {@link ResponseEntity} with status {@code 204 (No Content)}
     */
    @DeleteMapping("sessions")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteAllSessionsForCurrentUser() {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        long sessionCount = irisChatSessionRepository.countByUserId(user.getId());
        long messageCount = irisChatSessionRepository.countMessagesByUserId(user.getId());
        log.info("REST request to delete all Iris chat sessions for user id {} (sessions={}, messages={})", user.getId(), sessionCount, messageCount);
        irisChatSessionRepository.deleteAllByUserId(user.getId());
        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_ALL_IRIS_SESSIONS, "sessions=" + sessionCount, "messages=" + messageCount);
        auditEventRepository.add(auditEvent);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/iris/chat-history/sessions/{sessionId} : Delete a single Iris chat session.
     * Only the owner of the session can delete it.
     *
     * @param sessionId the ID of the session to delete
     * @return the {@link ResponseEntity} with status {@code 204 (No Content)}
     */
    @DeleteMapping("sessions/{sessionId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteSession(@PathVariable Long sessionId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        IrisChatSession session = irisChatSessionRepository.findById(sessionId).orElseThrow(() -> new EntityNotFoundException("Iris chat session", sessionId));
        if (user.getId() == null || session.getUserId() != user.getId().longValue()) {
            throw new AccessForbiddenAlertException("You do not have access to this Iris chat session.", "iris", "iris.forbidden");
        }
        log.info("REST request to delete Iris chat session {} for user id {}", sessionId, user.getId());
        irisChatSessionRepository.deleteById(sessionId);
        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_IRIS_SESSION, "sessionId=" + sessionId);
        auditEventRepository.add(auditEvent);
        return ResponseEntity.noContent().build();
    }
}
