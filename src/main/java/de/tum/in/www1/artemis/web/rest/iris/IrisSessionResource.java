package de.tum.in.www1.artemis.web.rest.iris;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.IrisSession;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

/**
 * REST controller for managing {@link IrisSession}.
 */
@RestController
@RequestMapping("api/iris/")
public class IrisSessionResource {

    private final Logger log = LoggerFactory.getLogger(IrisSessionResource.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    private final UserRepository userRepository;

    public IrisSessionResource(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService, IrisSessionRepository irisSessionRepository,
            UserRepository userRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
        this.userRepository = userRepository;
    }

    /**
     * GET programming-exercises/{exerciseId}/session: Retrieve the current iris session for the programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the exercise
     */
    @GetMapping("programming-exercises/{exerciseId}/sessions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<IrisSession> getCurrentSession(@PathVariable Long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, null);

        // var result = irisSessionRepository.findByExerciseId(exercise.getId());
        var result = new IrisSession();
        result.setId(ThreadLocalRandom.current().nextLong());
        result.setExercise(exercise);
        result.setUser(userRepository.getUser());
        return ResponseEntity.ok(result);
    }

    /**
     * POST programming-exercises/{exerciseId}/session: Retrieve the current iris session for the programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the exercise
     */
    @PostMapping("programming-exercises/{exerciseId}/sessions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<IrisSession> createSessionForProgrammingExercise(@PathVariable Long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, null);

        // TODO: Check that no session exists so far
        var result = new IrisSession();
        result.setId(ThreadLocalRandom.current().nextLong());
        result.setExercise(exercise);
        result.setUser(userRepository.getUser());
        // TODO: Set attributes
        // TODO: Save session
        return ResponseEntity.ok(result);
    }
}
