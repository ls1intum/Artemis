package de.tum.in.www1.artemis.web.rest.tutorialgroups;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import javax.validation.Valid;
import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupScheduleRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@RestController
@RequestMapping("/api")
public class TutorialGroupSessionResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final String ENTITY_NAME = "tutorialGroupSession";

    private final Logger log = LoggerFactory.getLogger(TutorialGroupSession.class);

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    private final TutorialGroupScheduleRepository tutorialGroupScheduleRepository;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public TutorialGroupSessionResource(TutorialGroupSessionRepository tutorialGroupSessionRepository, TutorialGroupScheduleRepository tutorialGroupScheduleRepository,
            TutorialGroupRepository tutorialGroupRepository, CourseRepository courseRepository, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService) {
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
        this.tutorialGroupScheduleRepository = tutorialGroupScheduleRepository;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * GET /courses/{courseId}/tutorial-groups/:tutorialGroupId/sessions/:sessionId : get the tutorial group session with the given id.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to which the session belongs to
     * @param sessionId       the id of the session to retrieve
     * @return ResponseEntity with status 200 (OK) and with body the tutorial group session
     */
    @GetMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupSession> getOneOfTutorialGroup(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable Long sessionId) {
        log.debug("REST request to get session: {} of tutorial group: {} of course {}", sessionId, tutorialGroupId, courseId);
        var session = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, session.getTutorialGroup().getCourse(), null);
        checkEntityIdMatchesPathIds(session, Optional.of(courseId), Optional.of(tutorialGroupId), Optional.of(sessionId));
        // prevent circular to json conversion
        if (session.getTutorialGroupSchedule() != null) {
            session.getTutorialGroupSchedule().setTutorialGroupSessions(null);
        }
        if (session.getTutorialGroup() != null) {
            session.getTutorialGroup().setTutorialGroupSessions(null);
            session.getTutorialGroup().setTutorialGroupSchedule(null);
        }
        return ResponseEntity.ok().body(session);
    }

    /**
     * PUT /courses/:courseId/tutorial-groups/:tutorialGroupId/sessions/:sessionId : Updates an existing tutorial group session
     *
     * @param courseId             the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId      the id of the tutorial group to update
     * @param sessionId            the id of the session to update
     * @param tutorialGroupSession the tutorial group session to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated tutorial group session
     */
    @PutMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupSession> update(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable Long sessionId,
            @RequestBody @Valid TutorialGroupSession tutorialGroupSession) {
        log.debug("REST request to update session: {} of tutorial group: {} of course {}", sessionId, tutorialGroupId, courseId);
        if (tutorialGroupSession.getId() == null) {
            throw new BadRequestException("A tutorial group session cannot be updated without an id");
        }
        var sessionFromDatabase = this.tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        checkEntityIdMatchesPathIds(sessionFromDatabase, Optional.of(courseId), Optional.of(tutorialGroupId), Optional.of(sessionId));
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, sessionFromDatabase.getTutorialGroup().getCourse(), null);
        // ToDo: Handle overlap case
        sessionFromDatabase.setStart(tutorialGroupSession.getStart());
        sessionFromDatabase.setEnd(tutorialGroupSession.getEnd());

        // if the session belongs to a schedule we have to cut the connection to mark that it does not follow the schedule anymore
        if (sessionFromDatabase.getTutorialGroupSchedule() != null) {
            var scheduleFromDatabase = tutorialGroupScheduleRepository.findByIdWithSessionsElseThrow(sessionFromDatabase.getTutorialGroupSchedule().getId());
            scheduleFromDatabase.getTutorialGroupSessions().remove(sessionFromDatabase);
            tutorialGroupScheduleRepository.save(scheduleFromDatabase);
            sessionFromDatabase.setTutorialGroupSchedule(null);
        }

        TutorialGroupSession result = tutorialGroupSessionRepository.save(sessionFromDatabase);

        return ResponseEntity.ok(result);
    }

    /**
     * POST /tutorial-groups/:tutorialGroupId/sessions : creates a new tutorial group.
     *
     * @param tutorialGroupSession the tutorial group that should be created
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group
     */
    @PostMapping("/tutorial-groups/{tutorialGroupId}/tutorial-group-sessions")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupSession> create(@PathVariable Long tutorialGroupId, @RequestBody @Valid TutorialGroupSession tutorialGroupSession)
            throws URISyntaxException {
        log.debug("REST request to create TutorialGroupSession: {} for tutorial group: {}", tutorialGroupSession);
        if (tutorialGroupSession.getId() != null) {
            throw new BadRequestException("A new tutorial group session cannot already have an ID");
        }
        var tutorialGroup = tutorialGroupRepository.findByIdWithSessionsElseThrow(tutorialGroupId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroup.getCourse(), null);
        if (tutorialGroup.getCourse().getTutorialGroupsConfiguration() == null) {
            throw new BadRequestException("The course has no tutorial groups configuration");
        }
        // ToDo: Check for overlapping sessions
        tutorialGroupSession.setTutorialGroup(tutorialGroup);
        tutorialGroupSession.setStatus(TutorialGroupSessionStatus.ACTIVE);

        tutorialGroupSessionRepository.save(tutorialGroupSession);

        return ResponseEntity.created(new URI("")).body(tutorialGroupSession);
    }

    @PostMapping("/tutorial-group-sessions/{tutorialGroupSessionId}/cancel")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupSession> cancel(@PathVariable Long tutorialGroupSessionId, @RequestBody StatusDTO statusDTO) throws URISyntaxException {

        log.debug("REST request to cancel TutorialGroupSession: {}", tutorialGroupSessionId);
        var tutorialGroupSession = tutorialGroupSessionRepository.findByIdElseThrow(tutorialGroupSessionId);

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupSession.getTutorialGroup().getCourse(), null);

        tutorialGroupSession.setStatus(TutorialGroupSessionStatus.CANCELLED);
        if (statusDTO != null && statusDTO.status_explanation() != null && statusDTO.status_explanation().trim().length() > 0) {
            tutorialGroupSession.setStatusExplanation(statusDTO.status_explanation().trim());
        }
        tutorialGroupSessionRepository.save(tutorialGroupSession);

        return ResponseEntity.ok().body(tutorialGroupSession);
    }

    @PostMapping("/tutorial-group-sessions/{tutorialGroupSessionId}/activate")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupSession> activate(@PathVariable Long tutorialGroupSessionId) throws URISyntaxException {
        log.debug("REST request to cancel TutorialGroupSession: {}", tutorialGroupSessionId);
        var tutorialGroupSession = tutorialGroupSessionRepository.findByIdElseThrow(tutorialGroupSessionId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupSession.getTutorialGroup().getCourse(), null);

        tutorialGroupSession.setStatus(TutorialGroupSessionStatus.ACTIVE);
        tutorialGroupSession.setStatusExplanation(null);

        tutorialGroupSessionRepository.save(tutorialGroupSession);

        return ResponseEntity.ok().body(tutorialGroupSession);
    }

    public record StatusDTO(String status_explanation) {
    }

    private void checkEntityIdMatchesPathIds(TutorialGroupSession tutorialGroupSession, Optional<Long> courseId, Optional<Long> tutorialGroupId, Optional<Long> sessionId) {
        sessionId.ifPresent(sessionIdValue -> {
            if (!tutorialGroupSession.getId().equals(sessionIdValue)) {
                throw new BadRequestAlertException("The session id in the path does not match the id in the tutorial group session", ENTITY_NAME, "sessionIdMismatch");
            }
        });
        tutorialGroupId.ifPresent(tutorialGroupIdValue -> {
            if (!tutorialGroupSession.getTutorialGroup().getId().equals(tutorialGroupIdValue)) {
                throw new BadRequestAlertException("The tutorialGroupId in the path does not match the tutorialGroupId in the tutorial group", ENTITY_NAME,
                        "tutorialGroupIdMismatch");
            }
        });
        courseId.ifPresent(courseIdValue -> {
            if (!tutorialGroupSession.getTutorialGroup().getCourse().getId().equals(courseIdValue)) {
                throw new BadRequestAlertException("The courseId in the path does not match the courseId in the tutorial group", ENTITY_NAME, "courseIdMismatch");
            }
        });
    }

}
