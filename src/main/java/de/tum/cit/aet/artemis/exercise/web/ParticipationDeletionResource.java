package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.security.Principal;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * REST controller for deleting a participation and cleaning up a build plan for a participation.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/exercise/")
public class ParticipationDeletionResource {

    private static final Logger log = LoggerFactory.getLogger(ParticipationDeletionResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ParticipationDeletionService participationDeletionService;

    private final FeatureToggleService featureToggleService;

    private final ParticipationAuthorizationService participationAuthorizationService;

    private final UserRepository userRepository;

    private final AuditEventRepository auditEventRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    public ParticipationDeletionResource(ParticipationDeletionService participationDeletionService, UserRepository userRepository,
            StudentParticipationRepository studentParticipationRepository, AuditEventRepository auditEventRepository, FeatureToggleService featureToggleService,
            ParticipationAuthorizationService participationAuthorizationService) {
        this.participationDeletionService = participationDeletionService;
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
        this.featureToggleService = featureToggleService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.participationAuthorizationService = participationAuthorizationService;
    }

    /**
     * DELETE /participations/:participationId : delete the "participationId" participation. This only works for student participations - other participations should not be deleted
     * here!
     *
     * @param participationId the participationId of the participation to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("participations/{participationId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteParticipation(@PathVariable Long participationId) {
        StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        if (participation instanceof ProgrammingExerciseParticipation && !featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)) {
            throw new AccessForbiddenException("Programming Exercise Feature is disabled.");
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        participationAuthorizationService.checkAccessPermissionAtLeastInstructor(participation, user);
        return deleteParticipation(participation, user);
    }

    /**
     * delete the participation, potentially including build plan and repository and log the event in the database audit
     *
     * @param participation the participation to be deleted
     * @param user          the currently logged-in user who initiated the delete operation
     * @return the response to the client
     */
    @NotNull
    private ResponseEntity<Void> deleteParticipation(StudentParticipation participation, User user) {
        String name = participation.getParticipantName();
        var logMessage = "Delete Participation " + participation.getId() + " of exercise " + participation.getExercise().getTitle() + " for " + name + " by " + user.getLogin();
        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_PARTICIPATION, logMessage);
        auditEventRepository.add(auditEvent);
        log.info(logMessage);
        participationDeletionService.delete(participation.getId(), true);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, "participation", name)).build();
    }

    /**
     * DELETE /participations/:participationId/cleanup-build-plan : remove the build plan of the ProgrammingExerciseStudentParticipation of the "participationId".
     * This only works for programming exercises.
     *
     * @param participationId the participationId of the ProgrammingExerciseStudentParticipation for which the build plan should be removed
     * @param principal       The identity of the user accessing this resource
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("participations/{participationId}/cleanup-build-plan")
    @EnforceAtLeastInstructor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Participation> cleanupBuildPlan(@PathVariable Long participationId, Principal principal) {
        ProgrammingExerciseStudentParticipation participation = (ProgrammingExerciseStudentParticipation) studentParticipationRepository.findByIdElseThrow(participationId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        participationAuthorizationService.checkAccessPermissionAtLeastInstructor(participation, user);
        log.info("Clean up participation with build plan {} by {}", participation.getBuildPlanId(), principal.getName());
        participationDeletionService.cleanupBuildPlan(participation);
        return ResponseEntity.ok().body(participation);
    }

}
