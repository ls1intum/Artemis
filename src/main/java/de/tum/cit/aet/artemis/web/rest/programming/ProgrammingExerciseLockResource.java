package de.tum.cit.aet.artemis.web.rest.programming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.service.messaging.InstanceMessageSendService;

// only available for external version control services
@Profile("!localvc & core")
@RestController
@RequestMapping("api/")
public class ProgrammingExerciseLockResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseLockResource.class);

    private final InstanceMessageSendService instanceMessageSendService;

    public ProgrammingExerciseLockResource(InstanceMessageSendService instanceMessageSendService) {
        this.instanceMessageSendService = instanceMessageSendService;
    }

    /**
     * Unlock all repositories of the given programming exercise (only necessary for external version control systems)
     * Locking and unlocking repositories is not supported when using the local version control system.
     * Repository access is checked in the LocalVCFetchFilter and LocalVCPushFilter.
     *
     * @param exerciseId of the exercise
     * @return The ResponseEntity with status 200 (OK) or with status 404 (Not Found) if the exerciseId is invalid
     */
    @PostMapping("programming-exercises/{exerciseId}/unlock-all-repositories")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<Void> unlockAllRepositories(@PathVariable Long exerciseId) {
        instanceMessageSendService.sendUnlockAllStudentRepositoriesAndParticipations(exerciseId);
        log.info("Unlocked all repositories of programming exercise {} upon manual request", exerciseId);
        return ResponseEntity.ok().build();
    }

    /**
     * Lock all repositories of the given programming exercise (only necessary for external version control systems)
     * Locking and unlocking repositories is not supported when using the local version control system.
     * Repository access is checked in the LocalVCFetchFilter and LocalVCPushFilter.
     *
     * @param exerciseId of the exercise
     * @return The ResponseEntity with status 200 (OK) or with status 404 (Not Found) if the exerciseId is invalid
     */
    @PostMapping("programming-exercises/{exerciseId}/lock-all-repositories")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<Void> lockAllRepositories(@PathVariable Long exerciseId) {
        instanceMessageSendService.sendLockAllStudentRepositoriesAndParticipations(exerciseId);
        log.info("Locked all repositories of programming exercise {} upon manual request", exerciseId);
        return ResponseEntity.ok().build();
    }

}
