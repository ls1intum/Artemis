package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.IrisSession;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisChatSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;

/**
 * REST controller for managing {@link IrisSession}.
 */
@RestController
@RequestMapping("api/iris/")
public class IrisSessionResource {

    private final Logger log = LoggerFactory.getLogger(IrisSessionResource.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final IrisChatSessionRepository irisChatSessionRepository;

    private final UserRepository userRepository;

    private final IrisSessionService irisSessionService;

    public IrisSessionResource(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            IrisChatSessionRepository irisChatSessionRepository, UserRepository userRepository, IrisSessionService irisSessionService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
    }

    /**
     * GET programming-exercises/{exerciseId}/session: Retrieve the current iris session for the programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("programming-exercises/{exerciseId}/sessions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<IrisSession> getCurrentSession(@PathVariable Long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        irisSessionService.checkIsIrisActivated(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);

        var session = irisChatSessionRepository.findByExerciseIdAndUserIdElseThrow(exercise.getId(), user.getId());
        irisSessionService.checkHasAccessToIrisSession(session, user);
        return ResponseEntity.ok(session);
    }

    /**
     * POST programming-exercises/{exerciseId}/session: Retrieve the current iris session for the programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new iris session for the exercise
     */
    @PostMapping("programming-exercises/{exerciseId}/sessions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<IrisSession> createSessionForProgrammingExercise(@PathVariable Long exerciseId) throws URISyntaxException {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        irisSessionService.checkIsIrisActivated(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        var session = irisSessionService.createChatSessionForProgrammingExercise(exercise, user);

        var uriString = "/api/iris/sessions/" + session.getId();
        return ResponseEntity.created(new URI(uriString)).body(session);
    }
}
