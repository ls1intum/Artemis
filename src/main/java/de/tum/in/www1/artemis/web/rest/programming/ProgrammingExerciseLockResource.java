package de.tum.in.www1.artemis.web.rest.programming;

import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.LOCK_ALL_REPOSITORIES;
import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.ROOT;
import static de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints.UNLOCK_ALL_REPOSITORIES;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;

// only available for external version control services
@Profile("!localvc & core")
@RestController
@RequestMapping(ROOT)
public class ProgrammingExerciseLockResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseLockResource.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final InstanceMessageSendService instanceMessageSendService;

    public ProgrammingExerciseLockResource(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            InstanceMessageSendService instanceMessageSendService) {

        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
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
    @PostMapping(UNLOCK_ALL_REPOSITORIES)
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> unlockAllRepositories(@PathVariable Long exerciseId) {
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, null);
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
    @PostMapping(LOCK_ALL_REPOSITORIES)
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> lockAllRepositories(@PathVariable Long exerciseId) {
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, null);
        instanceMessageSendService.sendLockAllStudentRepositoriesAndParticipations(exerciseId);
        log.info("Locked all repositories of programming exercise {} upon manual request", exerciseId);
        return ResponseEntity.ok().build();
    }

}
