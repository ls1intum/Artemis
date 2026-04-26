package de.tum.cit.aet.artemis.tutorialgroup.web;

import static de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupScheduleService.updateStatusAndFreePeriod;

import java.net.URISyntaxException;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.dto.CreateOrUpdateTutorialGroupSessionRequestDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupSessionDTO;
import de.tum.cit.aet.artemis.tutorialgroup.exception.SessionOverlapsWithSessionException;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupFreePeriodRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupScheduleRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupSessionRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupsConfigurationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupService;

@Conditional(TutorialGroupEnabled.class)
@Lazy
@RestController
@RequestMapping("api/tutorialgroup/")
public class TutorialGroupSessionResource {

    private static final String ENTITY_NAME = "tutorialGroupSession";

    private static final Logger log = LoggerFactory.getLogger(TutorialGroupSessionResource.class);

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    private final TutorialGroupScheduleRepository tutorialGroupScheduleRepository;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    private final TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    private final TutorialGroupService tutorialGroupService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public TutorialGroupSessionResource(TutorialGroupSessionRepository tutorialGroupSessionRepository, TutorialGroupScheduleRepository tutorialGroupScheduleRepository,
            TutorialGroupRepository tutorialGroupRepository, TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository,
            TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository, TutorialGroupService tutorialGroupService, CourseRepository courseRepository,
            UserRepository userRepository, AuthorizationCheckService authorizationCheckService) {
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
        this.tutorialGroupScheduleRepository = tutorialGroupScheduleRepository;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.tutorialGroupFreePeriodRepository = tutorialGroupFreePeriodRepository;
        this.tutorialGroupsConfigurationRepository = tutorialGroupsConfigurationRepository;
        this.tutorialGroupService = tutorialGroupService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * POST /courses/:courseId/tutorial-groups/:tutorialGroupId/sessions : creates a new tutorial group session.
     *
     * @param courseId                             the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId                      the id of the tutorial group to which the session belongs to
     * @param createTutorialGroupSessionRequestDTO DTO containing the new tutorial group session
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group session
     */
    @PostMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions")
    @EnforceAtLeastTutor
    public ResponseEntity<TutorialGroupSessionDTO> createSession(@PathVariable Long courseId, @PathVariable Long tutorialGroupId,
            @RequestBody @Valid CreateOrUpdateTutorialGroupSessionRequestDTO createTutorialGroupSessionRequestDTO) {
        log.debug("REST request to create TutorialGroupSession: {} for tutorial group: {}", createTutorialGroupSessionRequestDTO, tutorialGroupId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdWithSessionsAndScheduleElseThrow(tutorialGroupId);
        boolean userIsTutorOfGroup = tutorialGroup.getTeachingAssistant().equals(user);
        boolean userIsAtLeastEditorInCourse = authorizationCheckService.isAtLeastEditorInCourse(user.getLogin(), courseId);
        if (!userIsTutorOfGroup && !userIsAtLeastEditorInCourse) {
            throw new AccessForbiddenException("Only the tutor of a tutorial group or a user that is at least editor in the course can create sessions.");
        }

        createTutorialGroupSessionRequestDTO.validityCheck();

        ZoneId courseTimeZone = validateTutorialGroupConfiguration(courseId);
        TutorialGroupSession newSession = createTutorialGroupSessionRequestDTO.toEntity(courseTimeZone);
        newSession.setTutorialGroup(tutorialGroup);
        checkIfSessionMatchesPathIds(newSession, Optional.of(courseId), Optional.of(tutorialGroupId), Optional.empty());
        checkForOverlapWithOtherSessions(newSession, courseTimeZone);

        Optional<TutorialGroupFreePeriod> overlappingPeriodOptional = tutorialGroupFreePeriodRepository.findFirstOverlappingInSameCourse(tutorialGroup.getCourse(),
                newSession.getStart(), newSession.getEnd());
        updateStatusAndFreePeriod(newSession, overlappingPeriodOptional);
        newSession = tutorialGroupSessionRepository.save(newSession);

        return ResponseEntity.status(HttpStatus.CREATED).body(TutorialGroupSessionDTO.from(newSession, tutorialGroup.getTutorialGroupSchedule()));
    }

    /**
     * PUT /courses/:courseId/tutorial-groups/:tutorialGroupId/sessions/:sessionId : Updates an existing tutorial group session
     *
     * @param courseId                             the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId                      the id of the tutorial group to which the session belongs to
     * @param sessionId                            the id of the session to update
     * @param updateTutorialGroupSessionRequestDTO DTO containing the updated tutorial group session
     * @return the ResponseEntity with status 200 (OK) and with body the updated tutorial group session
     */
    @PutMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}")
    @EnforceAtLeastTutor
    public ResponseEntity<TutorialGroupSessionDTO> updateSession(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable Long sessionId,
            @RequestBody @Valid CreateOrUpdateTutorialGroupSessionRequestDTO updateTutorialGroupSessionRequestDTO) {
        log.debug("REST request to update session: {} of tutorial group: {} of course {}", sessionId, tutorialGroupId, courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdWithSessionsAndScheduleElseThrow(tutorialGroupId);
        boolean userIsTutorOfGroup = tutorialGroup.getTeachingAssistant().equals(user);
        boolean userIsAtLeastEditorInCourse = authorizationCheckService.isAtLeastEditorInCourse(user.getLogin(), courseId);
        if (!userIsTutorOfGroup && !userIsAtLeastEditorInCourse) {
            throw new AccessForbiddenException("Only the tutor of a tutorial group or a user that is at least editor in the course can update sessions.");
        }

        updateTutorialGroupSessionRequestDTO.validityCheck();

        var sessionToUpdate = this.tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        checkIfSessionMatchesPathIds(sessionToUpdate, Optional.of(courseId), Optional.of(tutorialGroupId), Optional.of(sessionId));

        ZoneId courseTimeZone = validateTutorialGroupConfiguration(courseId);
        var updatedSession = updateTutorialGroupSessionRequestDTO.toEntity(courseTimeZone);
        sessionToUpdate.setStart(updatedSession.getStart());
        sessionToUpdate.setEnd(updatedSession.getEnd());
        sessionToUpdate.setLocation(updatedSession.getLocation());
        sessionToUpdate.setAttendanceCount(updatedSession.getAttendanceCount());

        checkForOverlapWithOtherSessions(sessionToUpdate, courseTimeZone);

        // if the session belongs to a schedule we have to cut the connection to mark that it does not follow the schedule anymore
        if (sessionToUpdate.getTutorialGroupSchedule() != null) {
            var schedule = tutorialGroupScheduleRepository.findByIdWithSessionsElseThrow(sessionToUpdate.getTutorialGroupSchedule().getId());
            schedule.getTutorialGroupSessions().remove(sessionToUpdate);
            tutorialGroupScheduleRepository.save(schedule);
            sessionToUpdate.setTutorialGroupSchedule(null);
        }

        Optional<TutorialGroupFreePeriod> overlappingPeriodOptional = tutorialGroupFreePeriodRepository
                .findFirstOverlappingInSameCourse(sessionToUpdate.getTutorialGroup().getCourse(), sessionToUpdate.getStart(), sessionToUpdate.getEnd());
        updateStatusAndFreePeriod(sessionToUpdate, overlappingPeriodOptional);

        TutorialGroupSession result = tutorialGroupSessionRepository.save(sessionToUpdate);

        return ResponseEntity.ok(TutorialGroupSessionDTO.from(result, tutorialGroup.getTutorialGroupSchedule()));
    }

    /**
     * DELETE /courses/:courseId/tutorial-groups/:tutorialGroupId/sessions/:sessionId : delete a tutorial group session
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to which the session belongs to
     * @param sessionId       the id of the session to delete
     * @return the ResponseEntity with status 204 (NO_CONTENT)
     */
    @DeleteMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> deleteSession(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable Long sessionId) {
        log.debug("REST request to delete session: {} of tutorial group: {} of course {}", sessionId, tutorialGroupId, courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdWithSessionsAndScheduleElseThrow(tutorialGroupId);
        boolean userIsTutorOfGroup = tutorialGroup.getTeachingAssistant().equals(user);
        boolean userIsAtLeastEditorInCourse = authorizationCheckService.isAtLeastEditorInCourse(user.getLogin(), courseId);
        if (!userIsTutorOfGroup && !userIsAtLeastEditorInCourse) {
            throw new AccessForbiddenException("Only the tutor of a tutorial group or a user that is at least editor in the course can delete sessions.");
        }

        var sessionFromDatabase = this.tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        checkIfSessionMatchesPathIds(sessionFromDatabase, Optional.of(courseId), Optional.of(tutorialGroupId), Optional.of(sessionId));

        tutorialGroupSessionRepository.deleteById(sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /courses/:courseId/tutorial-groups/:tutorialGroupId/sessions/:sessionId/cancel : cancel a tutorial group session.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to which the session belongs to
     * @param sessionId       the id of the session to cancel
     * @param explanation     the explanation for the cancellation
     * @return ResponseEntity with status 200 (OK) and in the body the cancelled tutorial group session
     */
    @PatchMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}/cancel")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> cancelSession(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable Long sessionId,
            @RequestParam(required = false) String explanation) throws URISyntaxException {
        log.debug("REST request to cancel session: {} of tutorial group: {} of course {}", sessionId, tutorialGroupId, courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdWithSessionsAndScheduleElseThrow(tutorialGroupId);
        boolean userIsTutorOfGroup = tutorialGroup.getTeachingAssistant().equals(user);
        boolean userIsAtLeastEditorInCourse = authorizationCheckService.isAtLeastEditorInCourse(user.getLogin(), courseId);
        if (!userIsTutorOfGroup && !userIsAtLeastEditorInCourse) {
            throw new AccessForbiddenException("Only the tutor of a tutorial group or a user that is at least editor in the course can cancel sessions.");
        }

        var sessionToCancel = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        if (sessionToCancel.getTutorialGroupFreePeriod() != null) {
            throw new BadRequestException("You can not cancel a session that is cancelled by a overlapping with a free period");
        }
        checkIfSessionMatchesPathIds(sessionToCancel, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupId), Optional.of(sessionId));

        sessionToCancel.setStatus(TutorialGroupSessionStatus.CANCELLED);
        if (explanation != null && !explanation.trim().isEmpty()) {
            sessionToCancel.setStatusExplanation(explanation.trim());
        }
        tutorialGroupSessionRepository.save(sessionToCancel);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /courses/:courseId/tutorial-groups/:tutorialGroupId/sessions/:sessionId/activate : activate a tutorial group session.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to which the session belongs to
     * @param sessionId       the id of the session to activate
     * @return ResponseEntity with status 200 (OK) and in the body the activated tutorial group session
     */
    @PatchMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}/activate")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> activateSession(@PathVariable long courseId, @PathVariable long tutorialGroupId, @PathVariable long sessionId) throws URISyntaxException {
        log.debug("REST request to activate session: {} of tutorial group: {} of course {}", sessionId, tutorialGroupId, courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        TutorialGroup tutorialGroup = tutorialGroupRepository.findByIdWithSessionsAndScheduleElseThrow(tutorialGroupId);
        boolean userIsTutorOfGroup = tutorialGroup.getTeachingAssistant().equals(user);
        boolean userIsAtLeastEditorInCourse = authorizationCheckService.isAtLeastEditorInCourse(user.getLogin(), courseId);
        if (!userIsTutorOfGroup && !userIsAtLeastEditorInCourse) {
            throw new AccessForbiddenException("Only the tutor of a tutorial group or a user that is at least editor in the course can activate sessions.");
        }

        var sessionToActivate = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        if (sessionToActivate.getTutorialGroupFreePeriod() != null) {
            throw new BadRequestException("You can not activate a session that is cancelled by a overlapping with a free period");
        }
        checkIfSessionMatchesPathIds(sessionToActivate, Optional.of(courseId), Optional.of(tutorialGroupId), Optional.of(sessionId));

        sessionToActivate.setStatus(TutorialGroupSessionStatus.ACTIVE);
        sessionToActivate.setStatusExplanation(null);
        tutorialGroupSessionRepository.save(sessionToActivate);
        return ResponseEntity.noContent().build();
    }

    private void checkIfSessionMatchesPathIds(TutorialGroupSession tutorialGroupSession, Optional<Long> courseId, Optional<Long> tutorialGroupId, Optional<Long> sessionId) {
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

    private void checkForOverlapWithOtherSessions(TutorialGroupSession session, ZoneId zoneId) {
        var overlappingSessions = tutorialGroupSessionRepository.findOverlappingInSameTutorialGroup(session.getTutorialGroup(), session.getStart(), session.getEnd()).stream()
                .filter(overlappingSession -> !overlappingSession.getId().equals(session.getId())).collect(Collectors.toSet());
        if (!overlappingSessions.isEmpty()) {
            throw new SessionOverlapsWithSessionException(overlappingSessions, zoneId);
        }
    }

    private ZoneId validateTutorialGroupConfiguration(@PathVariable Long courseId) {
        var configurationOptional = this.tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(courseId);
        var configuration = configurationOptional.orElseThrow(() -> new BadRequestException("The course has no tutorial groups configuration"));
        if (configuration.getCourse().getTimeZone() == null) {
            throw new BadRequestException("The course has no time zone");
        }
        return ZoneId.of(configuration.getCourse().getTimeZone());
    }
}
