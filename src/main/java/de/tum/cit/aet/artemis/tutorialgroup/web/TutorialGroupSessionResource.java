package de.tum.cit.aet.artemis.tutorialgroup.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.util.DateUtil.interpretInTimeZone;
import static de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupScheduleService.updateTutorialGroupSession;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.exception.SessionOverlapsWithSessionException;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupFreePeriodRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupScheduleRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupSessionRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupsConfigurationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupService;

@Profile(PROFILE_CORE)
@FeatureToggle(Feature.TutorialGroups)
@RestController
@RequestMapping("api/")
public class TutorialGroupSessionResource {

    private static final String ENTITY_NAME = "tutorialGroupSession";

    private static final Logger log = LoggerFactory.getLogger(TutorialGroupSessionResource.class);

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    private final TutorialGroupScheduleRepository tutorialGroupScheduleRepository;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    private final TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    private final TutorialGroupService tutorialGroupService;

    private final AuthorizationCheckService authorizationCheckService;

    public TutorialGroupSessionResource(TutorialGroupSessionRepository tutorialGroupSessionRepository, TutorialGroupScheduleRepository tutorialGroupScheduleRepository,
            TutorialGroupRepository tutorialGroupRepository, TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository,
            TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository, TutorialGroupService tutorialGroupService,
            AuthorizationCheckService authorizationCheckService) {
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
        this.tutorialGroupScheduleRepository = tutorialGroupScheduleRepository;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.tutorialGroupFreePeriodRepository = tutorialGroupFreePeriodRepository;
        this.tutorialGroupsConfigurationRepository = tutorialGroupsConfigurationRepository;
        this.tutorialGroupService = tutorialGroupService;
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
    @GetMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}")
    @EnforceAtLeastStudent
    public ResponseEntity<TutorialGroupSession> getOneOfTutorialGroup(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable Long sessionId) {
        log.debug("REST request to get session: {} of tutorial group: {} of course {}", sessionId, tutorialGroupId, courseId);
        var session = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, session.getTutorialGroup().getCourse(), null);
        checkEntityIdMatchesPathIds(session, Optional.of(courseId), Optional.of(tutorialGroupId), Optional.of(sessionId));
        if (!tutorialGroupService.isAllowedToSeePrivateTutorialGroupInformation(session.getTutorialGroup(), null)) {
            session.hidePrivacySensitiveInformation();
        }
        return ResponseEntity.ok().body(TutorialGroupSession.preventCircularJsonConversion(session));
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
    @PutMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}")
    @EnforceAtLeastTutor
    public ResponseEntity<TutorialGroupSession> update(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable Long sessionId,
            @RequestBody @Valid TutorialGroupSessionDTO tutorialGroupSessionDTO) {
        log.debug("REST request to update session: {} of tutorial group: {} of course {}", sessionId, tutorialGroupId, courseId);
        tutorialGroupSessionDTO.validityCheck();

        var sessionToUpdate = this.tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        checkEntityIdMatchesPathIds(sessionToUpdate, Optional.of(courseId), Optional.of(tutorialGroupId), Optional.of(sessionId));
        tutorialGroupService.isAllowedToModifySessionsOfTutorialGroup(sessionToUpdate.getTutorialGroup(), null);

        TutorialGroupsConfiguration configuration = validateTutorialGroupConfiguration(courseId);
        var updatedSession = tutorialGroupSessionDTO.toEntity(configuration);
        sessionToUpdate.setStart(updatedSession.getStart());
        sessionToUpdate.setEnd(updatedSession.getEnd());
        sessionToUpdate.setLocation(updatedSession.getLocation());

        isValidTutorialGroupSession(sessionToUpdate, ZoneId.of(configuration.getCourse().getTimeZone()));

        // if the session belongs to a schedule we have to cut the connection to mark that it does not follow the schedule anymore
        if (sessionToUpdate.getTutorialGroupSchedule() != null) {
            var schedule = tutorialGroupScheduleRepository.findByIdWithSessionsElseThrow(sessionToUpdate.getTutorialGroupSchedule().getId());
            schedule.getTutorialGroupSessions().remove(sessionToUpdate);
            tutorialGroupScheduleRepository.save(schedule);
            sessionToUpdate.setTutorialGroupSchedule(null);
        }

        Optional<TutorialGroupFreePeriod> overlappingPeriodOptional = tutorialGroupFreePeriodRepository
                .findFirstOverlappingInSameCourse(sessionToUpdate.getTutorialGroup().getCourse(), sessionToUpdate.getStart(), sessionToUpdate.getEnd());
        updateTutorialGroupSession(sessionToUpdate, overlappingPeriodOptional);

        TutorialGroupSession result = tutorialGroupSessionRepository.save(sessionToUpdate);

        return ResponseEntity.ok(TutorialGroupSession.preventCircularJsonConversion(result));
    }

    /**
     * PATCH /courses/:courseId/tutorial-groups/:tutorialGroupId/sessions/:sessionId/attendance-count : Updates the attendance count of a tutorial group session
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to which the session belongs to
     * @param sessionId       the id of the session to update
     * @param attendanceCount the new attendance count, can be null
     * @return the ResponseEntity with status 200 (OK) and with body the updated tutorial group session
     */
    @PatchMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}/attendance-count")
    @EnforceAtLeastTutor
    public ResponseEntity<TutorialGroupSession> updateAttendanceCount(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable Long sessionId,
            @RequestParam(required = false) @Min(0) @Max(3000) Integer attendanceCount) {
        log.debug("REST request to update attendance count of session: {} of tutorial group: {} of course {} to {}", sessionId, tutorialGroupId, courseId, attendanceCount);
        var sessionToUpdate = this.tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        checkEntityIdMatchesPathIds(sessionToUpdate, Optional.of(courseId), Optional.of(tutorialGroupId), Optional.of(sessionId));
        tutorialGroupService.isAllowedToModifySessionsOfTutorialGroup(sessionToUpdate.getTutorialGroup(), null);
        sessionToUpdate.setAttendanceCount(attendanceCount);
        var result = tutorialGroupSessionRepository.save(sessionToUpdate);
        return ResponseEntity.ok(TutorialGroupSession.preventCircularJsonConversion(result));
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
        var sessionFromDatabase = this.tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        checkEntityIdMatchesPathIds(sessionFromDatabase, Optional.of(courseId), Optional.of(tutorialGroupId), Optional.of(sessionId));
        tutorialGroupService.isAllowedToChangeRegistrationsOfTutorialGroup(sessionFromDatabase.getTutorialGroup(), null);
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
    @PostMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions")
    @EnforceAtLeastTutor
    public ResponseEntity<TutorialGroupSession> create(@PathVariable Long courseId, @PathVariable Long tutorialGroupId,
            @RequestBody @Valid TutorialGroupSessionDTO tutorialGroupSessionDTO) throws URISyntaxException {
        log.debug("REST request to create TutorialGroupSession: {} for tutorial group: {}", tutorialGroupSessionDTO, tutorialGroupId);
        tutorialGroupSessionDTO.validityCheck();
        var tutorialGroup = tutorialGroupRepository.findByIdWithSessionsElseThrow(tutorialGroupId);
        tutorialGroupService.isAllowedToModifySessionsOfTutorialGroup(tutorialGroup, null);
        TutorialGroupsConfiguration configuration = validateTutorialGroupConfiguration(courseId);
        TutorialGroupSession newSession = tutorialGroupSessionDTO.toEntity(configuration);
        newSession.setTutorialGroup(tutorialGroup);
        checkEntityIdMatchesPathIds(newSession, Optional.of(courseId), Optional.of(tutorialGroupId), Optional.empty());
        isValidTutorialGroupSession(newSession, ZoneId.of(configuration.getCourse().getTimeZone()));

        Optional<TutorialGroupFreePeriod> overlappingPeriodOptional = tutorialGroupFreePeriodRepository.findFirstOverlappingInSameCourse(tutorialGroup.getCourse(),
                newSession.getStart(), newSession.getEnd());
        updateTutorialGroupSession(newSession, overlappingPeriodOptional);
        newSession = tutorialGroupSessionRepository.save(newSession);

        return ResponseEntity.created(URI.create("/api/courses/" + courseId + "/tutorial-groups/" + tutorialGroupId + "/sessions/" + newSession.getId()))
                .body(TutorialGroupSession.preventCircularJsonConversion(newSession));
    }

    private TutorialGroupsConfiguration validateTutorialGroupConfiguration(@PathVariable Long courseId) {
        var configurationOptional = this.tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(courseId);
        var configuration = configurationOptional.orElseThrow(() -> new BadRequestException("The course has no tutorial groups configuration"));
        if (configuration.getCourse().getTimeZone() == null) {
            throw new BadRequestException("The course has no time zone");
        }

        return configuration;
    }

    /**
     * POST /courses/:courseId/tutorial-groups/:tutorialGroupId/sessions/:sessionId/cancel : cancel a tutorial group session.
     *
     * @param courseId               the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId        the id of the tutorial group to which the session belongs to
     * @param sessionId              the id of the session to cancel
     * @param tutorialGroupStatusDTO DTO containing the explanation for the cancellation
     * @return ResponseEntity with status 200 (OK) and in the body the cancelled tutorial group session
     */
    @PostMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}/cancel")
    @EnforceAtLeastTutor
    public ResponseEntity<TutorialGroupSession> cancel(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable Long sessionId,
            @RequestBody TutorialGroupStatusDTO tutorialGroupStatusDTO) throws URISyntaxException {
        log.debug("REST request to cancel session: {} of tutorial group: {} of course {}", sessionId, tutorialGroupId, courseId);
        var sessionToCancel = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        if (sessionToCancel.getTutorialGroupFreePeriod() != null) {
            throw new BadRequestException("You can not cancel a session that is cancelled by a overlapping with a free period");
        }
        checkEntityIdMatchesPathIds(sessionToCancel, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupId), Optional.of(sessionId));
        tutorialGroupService.isAllowedToModifySessionsOfTutorialGroup(sessionToCancel.getTutorialGroup(), null);
        sessionToCancel.setStatus(TutorialGroupSessionStatus.CANCELLED);
        if (tutorialGroupStatusDTO != null && tutorialGroupStatusDTO.status_explanation() != null && !tutorialGroupStatusDTO.status_explanation().trim().isEmpty()) {
            sessionToCancel.setStatusExplanation(tutorialGroupStatusDTO.status_explanation().trim());
        }
        sessionToCancel = tutorialGroupSessionRepository.save(sessionToCancel);
        return ResponseEntity.ok().body(TutorialGroupSession.preventCircularJsonConversion(sessionToCancel));
    }

    /**
     * POST /courses/:courseId/tutorial-groups/:tutorialGroupId/sessions/:sessionId/activate : activate a tutorial group session.
     *
     * @param courseId        the id of the course to which the tutorial group belongs to
     * @param tutorialGroupId the id of the tutorial group to which the session belongs to
     * @param sessionId       the id of the session to activate
     * @return ResponseEntity with status 200 (OK) and in the body the activated tutorial group session
     */
    @PostMapping("courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}/activate")
    @EnforceAtLeastTutor
    public ResponseEntity<TutorialGroupSession> activate(@PathVariable long courseId, @PathVariable long tutorialGroupId, @PathVariable long sessionId) throws URISyntaxException {
        log.debug("REST request to activate session: {} of tutorial group: {} of course {}", sessionId, tutorialGroupId, courseId);
        var sessionToActivate = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        if (sessionToActivate.getTutorialGroupFreePeriod() != null) {
            throw new BadRequestException("You can not activate a session that is cancelled by a overlapping with a free period");
        }
        checkEntityIdMatchesPathIds(sessionToActivate, Optional.of(courseId), Optional.of(tutorialGroupId), Optional.of(sessionId));
        tutorialGroupService.isAllowedToModifySessionsOfTutorialGroup(sessionToActivate.getTutorialGroup(), null);
        sessionToActivate.setStatus(TutorialGroupSessionStatus.ACTIVE);
        sessionToActivate.setStatusExplanation(null);
        sessionToActivate = tutorialGroupSessionRepository.save(sessionToActivate);
        return ResponseEntity.ok().body(TutorialGroupSession.preventCircularJsonConversion(sessionToActivate));
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

    private void isValidTutorialGroupSession(TutorialGroupSession tutorialGroupSession, ZoneId zoneId) {
        this.checkForOverlapWithOtherSessions(tutorialGroupSession, zoneId);
    }

    private void checkForOverlapWithOtherSessions(TutorialGroupSession session, ZoneId zoneId) {
        var overlappingSessions = tutorialGroupSessionRepository.findOverlappingInSameTutorialGroup(session.getTutorialGroup(), session.getStart(), session.getEnd()).stream()
                .filter(overlappingSession -> !overlappingSession.getId().equals(session.getId())).collect(Collectors.toSet());
        if (!overlappingSessions.isEmpty()) {
            throw new SessionOverlapsWithSessionException(overlappingSessions, zoneId);
        }
    }

    /**
     * DTO used to send the status explanation when i.g. cancelling a tutorial group session
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupStatusDTO(String status_explanation) {
    }

    /**
     * DTO used because we want to interpret the dates in the time zone of the tutorial groups configuration
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TutorialGroupSessionDTO(@NotNull LocalDate date, @NotNull LocalTime startTime, @NotNull LocalTime endTime, @Size(min = 1, max = 2000) String location) {

        public void validityCheck() {
            if (startTime.isAfter(endTime)) {
                throw new BadRequestAlertException("The start time must be before the end time", ENTITY_NAME, "startTimeAfterEndTime");
            }
        }

        /**
         * Convert the DTO to a TutorialGroupSession object
         *
         * @param tutorialGroupsConfiguration the tutorial groups configuration to use for the conversion (needed for the time zone)
         * @return the converted TutorialGroupSession object
         */
        public TutorialGroupSession toEntity(TutorialGroupsConfiguration tutorialGroupsConfiguration) {
            TutorialGroupSession tutorialGroupSession = new TutorialGroupSession();
            tutorialGroupSession.setStart(interpretInTimeZone(date, startTime, tutorialGroupsConfiguration.getCourse().getTimeZone()));
            tutorialGroupSession.setEnd(interpretInTimeZone(date, endTime, tutorialGroupsConfiguration.getCourse().getTimeZone()));
            tutorialGroupSession.setLocation(location);
            return tutorialGroupSession;
        }

    }

}
