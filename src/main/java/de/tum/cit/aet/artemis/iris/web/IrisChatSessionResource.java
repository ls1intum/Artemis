package de.tum.cit.aet.artemis.iris.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionCountDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionResponseDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisCitationService;
import de.tum.cit.aet.artemis.iris.service.IrisSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

/**
 * REST controller for managing {@link IrisChatSession}.
 * Provides endpoints for session CRUD operations, chat history sidebar, and user-global operations.
 */
@Conditional(IrisEnabled.class)
@Lazy
@RestController
@RequestMapping("api/iris/chat/")
public class IrisChatSessionResource {

    private static final Logger log = LoggerFactory.getLogger(IrisChatSessionResource.class);

    private final UserRepository userRepository;

    private final IrisSessionService irisSessionService;

    private final IrisSettingsService irisSettingsService;

    private final CourseRepository courseRepository;

    private final IrisSessionRepository irisSessionRepository;

    private final IrisCitationService irisCitationService;

    private final IrisChatSessionRepository irisChatSessionRepository;

    private final CustomAuditEventRepository auditEventRepository;

    private final IrisChatSessionService irisChatSessionService;

    public IrisChatSessionResource(UserRepository userRepository, CourseRepository courseRepository, IrisSessionService irisSessionService, IrisSettingsService irisSettingsService,
            IrisSessionRepository irisSessionRepository, IrisCitationService irisCitationService, IrisChatSessionRepository irisChatSessionRepository,
            CustomAuditEventRepository auditEventRepository, IrisChatSessionService irisChatSessionService) {
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.courseRepository = courseRepository;
        this.irisSessionRepository = irisSessionRepository;
        this.irisCitationService = irisCitationService;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.auditEventRepository = auditEventRepository;
        this.irisChatSessionService = irisChatSessionService;
    }

    // -------------------------------------------------------------------------
    // Course-scoped endpoints (require courseId path variable)
    // -------------------------------------------------------------------------

    /**
     * POST api/iris/chat/{courseId}/sessions/current: Retrieve or create the current Iris chat session.
     *
     * @param courseId the course ID (required for authorization)
     * @param mode     the chat mode (e.g. COURSE_CHAT, PROGRAMMING_EXERCISE_CHAT)
     * @param entityId the exercise or lecture ID; omit for COURSE_CHAT
     * @return the current or newly created session
     */
    @PostMapping("{courseId}/sessions/current")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<IrisChatSessionResponseDTO> getCurrentSessionOrCreateIfNotExists(@PathVariable Long courseId, @RequestParam IrisChatMode mode,
            @RequestParam(required = false) Long entityId) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(courseId, mode, entityId, user);
        irisCitationService.enrichSessionWithCitationInfo(session);
        return ResponseEntity.ok(IrisChatSessionResponseDTO.ofWithMessages(session));
    }

    /**
     * POST api/iris/chat/{courseId}/sessions: Create a new Iris chat session.
     *
     * @param courseId the course ID (required for authorization)
     * @param mode     the chat mode (e.g. COURSE_CHAT, PROGRAMMING_EXERCISE_CHAT)
     * @param entityId the exercise or lecture ID; omit for COURSE_CHAT
     * @return the newly created session
     */
    @PostMapping("{courseId}/sessions")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<IrisChatSessionResponseDTO> createSession(@PathVariable Long courseId, @RequestParam IrisChatMode mode, @RequestParam(required = false) Long entityId)
            throws URISyntaxException {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var session = irisChatSessionService.createSession(courseId, mode, entityId, user);
        var uriString = "/api/iris/sessions/" + session.getId();
        return ResponseEntity.created(new URI(uriString)).body(IrisChatSessionResponseDTO.of(session));
    }

    /**
     * GET api/iris/chat/{courseId}/session/{sessionId}: Retrieve an Iris Session by id.
     *
     * @param courseId  of the course
     * @param sessionId of the session
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the iris session
     */
    @GetMapping("{courseId}/session/{sessionId}")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<IrisChatSessionResponseDTO> getSessionById(@PathVariable Long courseId, @PathVariable Long sessionId) {
        IrisSession irisSession = irisSessionRepository.findByIdWithMessagesAndContents(sessionId);

        if (irisSession == null) {
            throw new EntityNotFoundException("Iris session with id " + sessionId + " not found");
        }

        var user = userRepository.getUserWithGroupsAndAuthorities();
        irisSessionService.checkHasAccessToIrisSession(irisSession, user);

        boolean enabled = irisSettingsService.isEnabledForCourse(courseId);

        if (enabled) {
            if (!(irisSession instanceof IrisChatSession chatSession)) {
                throw new BadRequestException("Session is not a chat session");
            }
            chatSession.setCitationInfo(irisCitationService.resolveCitationInfoFromMessages(chatSession.getMessages()));
            return ResponseEntity.ok(IrisChatSessionResponseDTO.ofWithMessages(chatSession));
        }
        throw new AccessForbiddenAlertException("This Iris chat Type is disabled in the course.", "iris", "iris.disabled");
    }

    /**
     * GET api/iris/chat/{courseId}/sessions/overview: Retrieve all Iris Sessions as DTOs for the sidebar.
     *
     * @param courseId of the course
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of session DTOs
     */
    @GetMapping("{courseId}/sessions/overview")
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

    // -------------------------------------------------------------------------
    // User-global endpoints (no courseId)
    // -------------------------------------------------------------------------

    /**
     * GET /api/iris/chat/sessions/count : Get the number of sessions and messages for the current user.
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
     * DELETE /api/iris/chat/sessions : Delete all Iris chat sessions for the current user.
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
     * DELETE /api/iris/chat/sessions/{sessionId} : Delete a single Iris chat session.
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
        if (user.getId() == null || session.getUserId() != user.getId()) {
            throw new AccessForbiddenAlertException("You do not have access to this Iris chat session.", "iris", "iris.forbidden");
        }
        log.info("REST request to delete Iris chat session {} for user id {}", sessionId, user.getId());
        irisChatSessionRepository.deleteById(sessionId);
        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_IRIS_SESSION, "sessionId=" + sessionId);
        auditEventRepository.add(auditEvent);
        return ResponseEntity.noContent().build();
    }
}
