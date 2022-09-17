package de.tum.in.www1.artemis.web.rest.tutorialgroups;

import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.interpretInTimeZoneOfConfiguration;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupFreePeriodRepository;
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

    private static final String ENTITY_NAME = "tutorialGroupSession";

    private final Logger log = LoggerFactory.getLogger(TutorialGroupSession.class);

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    private final TutorialGroupScheduleRepository tutorialGroupScheduleRepository;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public TutorialGroupSessionResource(TutorialGroupSessionRepository tutorialGroupSessionRepository, TutorialGroupScheduleRepository tutorialGroupScheduleRepository,
            TutorialGroupRepository tutorialGroupRepository, TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository,
            AuthorizationCheckService authorizationCheckService) {
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
        this.tutorialGroupScheduleRepository = tutorialGroupScheduleRepository;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.tutorialGroupFreePeriodRepository = tutorialGroupFreePeriodRepository;
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
     * @param courseId                the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId         the id of the tutorial group to which the session belongs to
     * @param sessionId               the id of the session to update
     * @param tutorialGroupSessionDTO DTO containing the updated tutorial group session
     * @return the ResponseEntity with status 200 (OK) and with body the updated tutorial group session
     */
    @PutMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupSession> update(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable Long sessionId,
            @RequestBody TutorialGroupSessionDTO tutorialGroupSessionDTO) {
        log.debug("REST request to update session: {} of tutorial group: {} of course {}", sessionId, tutorialGroupId, courseId);

        var sessionToUpdate = this.tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        checkEntityIdMatchesPathIds(sessionToUpdate, Optional.of(courseId), Optional.of(tutorialGroupId), Optional.of(sessionId));
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, sessionToUpdate.getTutorialGroup().getCourse(), null);

        var updatedSession = tutorialGroupSessionDTO.toEntity(sessionToUpdate.getTutorialGroup().getCourse().getTutorialGroupsConfiguration());
        sessionToUpdate.setStart(updatedSession.getStart());
        sessionToUpdate.setEnd(updatedSession.getEnd());
        sessionToUpdate.setLocation(updatedSession.getLocation());

        isValidTutorialGroupSession(sessionToUpdate);

        // if the session belongs to a schedule we have to cut the connection to mark that it does not follow the schedule anymore
        if (sessionToUpdate.getTutorialGroupSchedule() != null) {
            var schedule = tutorialGroupScheduleRepository.findByIdWithSessionsElseThrow(sessionToUpdate.getTutorialGroupSchedule().getId());
            schedule.getTutorialGroupSessions().remove(sessionToUpdate);
            tutorialGroupScheduleRepository.save(schedule);
            sessionToUpdate.setTutorialGroupSchedule(null);
        }

        var overlappingPeriod = tutorialGroupFreePeriodRepository.findOverlappingInSameCourse(sessionToUpdate.getTutorialGroup().getCourse(), sessionToUpdate.getStart(),
                sessionToUpdate.getEnd());
        if (overlappingPeriod.isPresent()) {
            sessionToUpdate.setStatus(TutorialGroupSessionStatus.CANCELLED);
            sessionToUpdate.setStatusExplanation(overlappingPeriod.get().getReason());
        }
        else {
            sessionToUpdate.setStatus(TutorialGroupSessionStatus.ACTIVE);
            sessionToUpdate.setStatusExplanation(null);
        }

        TutorialGroupSession result = tutorialGroupSessionRepository.save(sessionToUpdate);

        return ResponseEntity.ok(result);
    }

    /**
     * DELETE /courses/:courseId/tutorial-groups/:tutorialGroupId/sessions/:sessionId : delete a tutorial group session
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to which the session belongs to
     * @param sessionId       the id of the session to delete
     * @return the ResponseEntity with status 204 (NO_CONTENT)
     */
    @DeleteMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<Void> deleteSession(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable Long sessionId) {
        log.debug("REST request to delete session: {} of tutorial group: {} of course {}", sessionId, tutorialGroupId, courseId);
        var sessionFromDatabase = this.tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        checkEntityIdMatchesPathIds(sessionFromDatabase, Optional.of(courseId), Optional.of(tutorialGroupId), Optional.of(sessionId));
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, sessionFromDatabase.getTutorialGroup().getCourse(), null);
        tutorialGroupSessionRepository.deleteById(sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /courses/:courseId/tutorial-groups/:tutorialGroupId/sessions : creates a new tutorial group session.
     *
     * @param courseId                the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId         the id of the tutorial group to which the session belongs to
     * @param tutorialGroupSessionDTO DTO containing the new tutorial group session
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group session
     */
    @PostMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupSession> create(@PathVariable Long courseId, @PathVariable Long tutorialGroupId,
            @RequestBody TutorialGroupSessionDTO tutorialGroupSessionDTO) throws URISyntaxException {
        log.debug("REST request to create TutorialGroupSession: {} for tutorial group: {}", tutorialGroupSessionDTO, tutorialGroupId);
        var tutorialGroup = tutorialGroupRepository.findByIdWithSessionsElseThrow(tutorialGroupId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroup.getCourse(), null);
        if (tutorialGroup.getCourse().getTutorialGroupsConfiguration() == null) {
            throw new BadRequestException("The course has no tutorial groups configuration");
        }
        TutorialGroupSession newSession = tutorialGroupSessionDTO.toEntity(tutorialGroup.getCourse().getTutorialGroupsConfiguration());
        newSession.setTutorialGroup(tutorialGroup);
        checkEntityIdMatchesPathIds(newSession, Optional.of(courseId), Optional.of(tutorialGroupId), Optional.empty());
        isValidTutorialGroupSession(newSession);

        var overlappingPeriod = tutorialGroupFreePeriodRepository.findOverlappingInSameCourse(tutorialGroup.getCourse(), newSession.getStart(), newSession.getEnd());
        if (overlappingPeriod.isPresent()) {
            newSession.setStatus(TutorialGroupSessionStatus.CANCELLED);
            newSession.setStatusExplanation(overlappingPeriod.get().getReason());
        }
        else {
            newSession.setStatus(TutorialGroupSessionStatus.ACTIVE);
            newSession.setStatusExplanation(null);
        }
        tutorialGroupSessionRepository.save(newSession);

        return ResponseEntity.created(URI.create("/api/courses/" + courseId + "/tutorial-groups/" + tutorialGroupId + "/sessions/" + newSession.getId())).body(newSession);
    }

    /**
     * POST /courses/:courseId/tutorial-groups/:tutorialGroupId/sessions/:sessionId/cancel : cancel a tutorial group session.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to which the session belongs to
     * @param sessionId       the id of the session to cancel
     * @param statusDTO       DTO containing the explanation for the cancellation
     * @return ResponseEntity with status 200 (OK) and in the body the cancelled tutorial group session
     */
    @PostMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}/cancel")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupSession> cancel(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable Long sessionId,
            @RequestBody StatusDTO statusDTO) throws URISyntaxException {
        log.debug("REST request to cancel session: {} of tutorial group: {} of course {}", sessionId, tutorialGroupId, courseId);
        var sessionToCancel = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        checkEntityIdMatchesPathIds(sessionToCancel, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupId), Optional.of(sessionId));
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, sessionToCancel.getTutorialGroup().getCourse(), null);
        sessionToCancel.setStatus(TutorialGroupSessionStatus.CANCELLED);
        if (statusDTO != null && statusDTO.status_explanation() != null && statusDTO.status_explanation().trim().length() > 0) {
            sessionToCancel.setStatusExplanation(statusDTO.status_explanation().trim());
        }
        tutorialGroupSessionRepository.save(sessionToCancel);
        return ResponseEntity.ok().body(sessionToCancel);
    }

    /**
     * POST /courses/:courseId/tutorial-groups/:tutorialGroupId/sessions/:sessionId/activate : activate a tutorial group session.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to which the session belongs to
     * @param sessionId       the id of the session to activate
     * @return ResponseEntity with status 200 (OK) and in the body the activated tutorial group session
     */
    @PostMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}/activate")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupSession> activate(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable Long sessionId) throws URISyntaxException {
        log.debug("REST request to activate session: {} of tutorial group: {} of course {}", sessionId, tutorialGroupId, courseId);
        var sessionToActivate = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        checkEntityIdMatchesPathIds(sessionToActivate, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupId), Optional.ofNullable(sessionId));
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, sessionToActivate.getTutorialGroup().getCourse(), null);
        sessionToActivate.setStatus(TutorialGroupSessionStatus.ACTIVE);
        sessionToActivate.setStatusExplanation(null);
        tutorialGroupSessionRepository.save(sessionToActivate);
        return ResponseEntity.ok().body(sessionToActivate);
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

    private void isValidTutorialGroupSession(TutorialGroupSession tutorialGroupSession) {
        if (tutorialGroupSession.getStart() == null || tutorialGroupSession.getEnd() == null) {
            throw new BadRequestException("Tutorial session start and end must be set");
        }
        if (tutorialGroupSession.getStart().isAfter(tutorialGroupSession.getEnd())) {
            throw new BadRequestException("Tutorial session start must be before tutorial period end");
        }
        this.checkForOverlapWithOtherSessions(tutorialGroupSession);
    }

    private void checkForOverlapWithOtherSessions(TutorialGroupSession session) {
        var overlappingSessions = tutorialGroupSessionRepository.findOverlappingInSameTutorialGroup(session.getTutorialGroup(), session.getStart(), session.getEnd()).stream()
                .filter(overlappingSession -> !overlappingSession.getId().equals(session.getId())).toList();
        if (!overlappingSessions.isEmpty()) {
            throw new BadRequestAlertException("The given session overlaps with another session in the same tutorial group", ENTITY_NAME, "overlapping");
        }
    }

    /**
     * DTO used to send the status explanation when i.g. cancelling a tutorial group session
     */
    private record StatusDTO(String status_explanation) {
    }

    /**
     * DTO used because we want to interpret the dates in the time zone of the tutorial groups configuration
     */
    private record TutorialGroupSessionDTO(LocalDate date, LocalTime startTime, LocalTime endTime, String location) {

        public TutorialGroupSession toEntity(TutorialGroupsConfiguration tutorialGroupsConfiguration) {
            TutorialGroupSession tutorialGroupSession = new TutorialGroupSession();
            tutorialGroupSession.setStart(interpretInTimeZoneOfConfiguration(date, startTime, tutorialGroupsConfiguration));
            tutorialGroupSession.setEnd(interpretInTimeZoneOfConfiguration(date, endTime, tutorialGroupsConfiguration));
            tutorialGroupSession.setLocation(location);
            return tutorialGroupSession;
        }

    }

}
