package de.tum.in.www1.artemis.web.rest.iris;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.iris.IrisChatSessionRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisHealthIndicator;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisStatusDTO;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;
import de.tum.in.www1.artemis.service.iris.IrisSettingsService;

/**
 * REST controller for managing {@link IrisSession}.
 */
@RestController
@Profile("iris")
@RequestMapping("api/iris/")
public class IrisSessionResource {

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final IrisChatSessionRepository irisChatSessionRepository;

    private final UserRepository userRepository;

    private final IrisSessionService irisSessionService;

    private final IrisSettingsService irisSettingsService;

    private final IrisHealthIndicator irisHealthIndicator;

    public IrisSessionResource(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            IrisChatSessionRepository irisChatSessionRepository, UserRepository userRepository, IrisSessionService irisSessionService, IrisSettingsService irisSettingsService,
            IrisHealthIndicator irisHealthIndicator) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.userRepository = userRepository;
        this.irisSessionService = irisSessionService;
        this.irisSettingsService = irisSettingsService;
        this.irisHealthIndicator = irisHealthIndicator;
    }

    /**
     * GET programming-exercises/{exerciseId}/sessions/current: Retrieve the current iris session for the programming exercise.
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the current iris session for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("programming-exercises/{exerciseId}/sessions/current")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisSession> getCurrentSession(@PathVariable Long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        irisSettingsService.checkIsIrisChatSessionEnabledElseThrow(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);

        var session = irisChatSessionRepository.findNewestByExerciseIdAndUserIdElseThrow(exercise.getId(), user.getId());
        irisSessionService.checkHasAccessToIrisSession(session, user);
        return ResponseEntity.ok(session);
    }

    /**
     * GET programming-exercises/{exerciseId}/sessions: Retrieve all Iris Sessions for the programming exercise
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a list of the iris sessions for the exercise or {@code 404 (Not Found)} if no session exists
     */
    @GetMapping("programming-exercises/{exerciseId}/sessions")
    @EnforceAtLeastStudent
    public ResponseEntity<List<IrisSession>> getAllSessions(@PathVariable Long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        irisSettingsService.checkIsIrisChatSessionEnabledElseThrow(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);

        var sessions = irisChatSessionRepository.findByExerciseIdAndUserIdElseThrow(exercise.getId(), user.getId());
        sessions.forEach(s -> irisSessionService.checkHasAccessToIrisSession(s, user));
        return ResponseEntity.ok((List<IrisSession>) (List<?>) sessions);
    }

    /**
     * POST programming-exercises/{exerciseId}/session: Create a new iris session for an exercise and user.
     * If there already exists an iris session for the exercise and user, a new one is created.
     * Note: The old session including messages is not deleted and can still be retrieved
     *
     * @param exerciseId of the exercise
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the new iris session for the exercise
     */
    @PostMapping("programming-exercises/{exerciseId}/sessions")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisSession> createSessionForProgrammingExercise(@PathVariable Long exerciseId) throws URISyntaxException {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        irisSettingsService.checkIsIrisChatSessionEnabledElseThrow(exercise);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);

        var session = irisSessionService.createChatSessionForProgrammingExercise(exercise, user);

        var uriString = "/api/iris/sessions/" + session.getId();
        return ResponseEntity.created(new URI(uriString)).body(session);
    }

    /**
     * GET iris/sessions/{sessionId}/active: Retrieve if Iris is active for a session
     * This checks if the used model is healthy.
     *
     * @param sessionId id of the session
     * @return a status {@code 200 (Ok)} and with body true if Iris is active, false otherwise
     */
    @GetMapping("/sessions/{sessionId}/active")
    @EnforceAtLeastStudent
    public ResponseEntity<Boolean> isIrisActive(@PathVariable Long sessionId) {
        var session = irisChatSessionRepository.findByIdElseThrow(sessionId);
        var user = userRepository.getUser();
        irisSessionService.checkHasAccessToIrisSession(session, user);
        irisSessionService.checkIsIrisActivated(session);
        var settings = irisSettingsService.getCombinedIrisSettings(session.getExercise(), false);
        var health = irisHealthIndicator.health();
        IrisStatusDTO[] modelStatuses = (IrisStatusDTO[]) health.getDetails().get("modelStatuses");
        var specificModelStatus = false;
        if (modelStatuses != null) {
            specificModelStatus = Arrays.stream(modelStatuses).filter(x -> x.model().equals(settings.getIrisChatSettings().getPreferredModel()))
                    .anyMatch(x -> x.status() == IrisStatusDTO.ModelStatus.UP);
        }
        return ResponseEntity.ok(specificModelStatus);
    }
}
